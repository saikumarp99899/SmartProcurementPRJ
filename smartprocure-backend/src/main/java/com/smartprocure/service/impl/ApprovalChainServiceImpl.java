package com.smartprocure.service.impl;

import com.smartprocure.entity.Approval;
import com.smartprocure.entity.Approval.ApprovalStatus;
import com.smartprocure.entity.ApprovalWorkflow;
import com.smartprocure.entity.ApprovalWorkflowStep;
import com.smartprocure.entity.Requisition;
import com.smartprocure.entity.User;
import com.smartprocure.exception.BusinessRuleException;
import com.smartprocure.repository.ApprovalRepository;
import com.smartprocure.repository.ApprovalWorkflowRepository;
import com.smartprocure.service.ApprovalChainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Turns workflow configuration into a concrete approval chain.
 *
 * Kept separate from RequisitionServiceImpl because workflow selection and
 * approver resolution are the most rule-heavy part of the process and benefit
 * from being tested on their own.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalChainServiceImpl implements ApprovalChainService {

    private final ApprovalWorkflowRepository workflowRepository;
    private final ApprovalRepository approvalRepository;

    /**
     * Precedence when several workflows match, in order:
     * 1. Higher priority wins.
     * 2. A department-specific workflow beats a department-agnostic one.
     * 3. Lower id wins, purely so the outcome is deterministic.
     */
    private static final Comparator<ApprovalWorkflow> SELECTION_ORDER =
            Comparator.comparing(ApprovalWorkflow::getPriority, Comparator.reverseOrder())
                    .thenComparing(w -> w.isDepartmentSpecific() ? 0 : 1)
                    .thenComparing(ApprovalWorkflow::getId);

    @Override
    @Transactional(readOnly = true)
    public Optional<ApprovalWorkflow> selectWorkflow(BigDecimal amount, Long departmentId) {
        if (amount == null) return Optional.empty();

        List<ApprovalWorkflow> candidates = workflowRepository.findCandidates(amount, departmentId);
        Optional<ApprovalWorkflow> selected = candidates.stream().min(SELECTION_ORDER);

        selected.ifPresentOrElse(
                w -> log.debug("Selected workflow '{}' (priority {}) from {} candidate(s) for amount {}",
                        w.getName(), w.getPriority(), candidates.size(), amount),
                () -> log.debug("No workflow matches amount {} and department {}", amount, departmentId));

        return selected;
    }

    @Override
    @Transactional
    public void buildChain(Requisition requisition) {
        BigDecimal amount = requisition.getTotalAmount();
        Long departmentId = requisition.getDepartment() != null
                ? requisition.getDepartment().getId() : null;

        Optional<ApprovalWorkflow> match = selectWorkflow(amount, departmentId);

        if (match.isEmpty()) {
            createFallbackApproval(requisition);
            return;
        }

        ApprovalWorkflow workflow = match.get();
        List<Approval> chain = resolveSteps(workflow, requisition);

        boolean anyActionable = chain.stream()
                .anyMatch(a -> a.getStatus() != ApprovalStatus.SKIPPED);

        // Every step resolving to the requester would leave the requisition
        // permanently stuck, so surface it instead of saving a dead chain.
        if (!anyActionable) {
            log.error("Workflow '{}' produced no actionable steps for requisition {} — every step "
                            + "resolved to the requester ({})",
                    workflow.getName(), requisition.getRequisitionNumber(),
                    requisition.getRequester().getEmail());
            throw new BusinessRuleException(
                    "Workflow \"" + workflow.getName() + "\" has no step that someone other than you "
                            + "can approve. Ask an administrator to add an approver to this workflow.");
        }

        // Promote the first non-skipped step so exactly one level is actionable.
        chain.stream()
                .filter(a -> a.getStatus() != ApprovalStatus.SKIPPED)
                .findFirst()
                .ifPresent(first -> first.setStatus(ApprovalStatus.PENDING));

        approvalRepository.saveAll(chain);

        log.info("Approval chain built for requisition {} using workflow '{}' (amount {}): {} step(s), {} skipped",
                requisition.getRequisitionNumber(), workflow.getName(), amount,
                chain.size(),
                chain.stream().filter(a -> a.getStatus() == ApprovalStatus.SKIPPED).count());
    }

    /**
     * Maps each configured step onto an Approval row, resolving who should act.
     * All rows start WAITING; the caller promotes the first actionable one.
     */
    private List<Approval> resolveSteps(ApprovalWorkflow workflow, Requisition requisition) {
        User requester = requisition.getRequester();
        List<Approval> chain = new ArrayList<>();

        for (ApprovalWorkflowStep step : workflow.getSteps()) {
            Approval approval = Approval.builder()
                    .requisition(requisition)
                    .approvalLevel(step.getStepOrder())
                    .ruleName(step.getName())
                    .workflowName(workflow.getName())
                    .status(ApprovalStatus.WAITING)
                    .build();

            switch (step.getApproverType()) {
                case SPECIFIC_USER -> approval.setAssignedApprover(step.getApprover());

                // Left unassigned to an individual: any active holder of the
                // role may act, which is checked at action time.
                case ROLE -> approval.setAssignedRole(step.getApproverRole());

                case REQUESTER_MANAGER -> {
                    User manager = requester.getManager();
                    if (manager == null) {
                        log.error("Step '{}' of workflow '{}' needs the requester's manager, but {} has none set",
                                step.getName(), workflow.getName(), requester.getEmail());
                        throw new BusinessRuleException(
                                "This requisition must be approved by your manager, but no manager is set "
                                        + "on your account. Ask an administrator to set your reporting manager.");
                    }
                    approval.setAssignedApprover(manager);
                }
            }

            // A named individual who is also the requester can never action
            // the step, so skip it rather than deadlock the chain. Role-based
            // steps are left alone because other holders can still act.
            User assigned = approval.getAssignedApprover();
            if (assigned != null && assigned.getId().equals(requester.getId())) {
                approval.setStatus(ApprovalStatus.SKIPPED);
                approval.setComments("Skipped — the requester cannot approve their own requisition");
                log.info("Skipping step '{}' for requisition {}: resolved approver is the requester ({})",
                        step.getName(), requisition.getRequisitionNumber(), requester.getEmail());
            }

            chain.add(approval);
        }

        return chain;
    }

    /**
     * Used when no workflow matches. Creating one open approval keeps the
     * system usable before any workflow is configured; the warning and the
     * UI both make the unconfigured state visible rather than silent.
     */
    private void createFallbackApproval(Requisition requisition) {
        log.warn("No approval workflow matches amount {} for requisition {} — creating a single "
                        + "approval open to any approver. Configure a workflow to route this explicitly.",
                requisition.getTotalAmount(), requisition.getRequisitionNumber());

        approvalRepository.save(Approval.builder()
                .requisition(requisition)
                .approvalLevel(1)
                .ruleName("Unrouted approval")
                .status(ApprovalStatus.PENDING)
                .build());
    }
}
