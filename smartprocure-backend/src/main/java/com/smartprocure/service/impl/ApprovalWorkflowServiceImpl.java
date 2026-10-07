package com.smartprocure.service.impl;

import com.smartprocure.dto.request.ApprovalWorkflowRequest;
import com.smartprocure.dto.response.ApprovalWorkflowResponse;
import com.smartprocure.dto.response.ApprovalWorkflowResponse.StepResponse;
import com.smartprocure.entity.*;
import com.smartprocure.entity.ApprovalWorkflowStep.ApproverType;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.exception.BadRequestException;
import com.smartprocure.exception.BusinessRuleException;
import com.smartprocure.exception.DuplicateResourceException;
import com.smartprocure.exception.ResourceNotFoundException;
import com.smartprocure.repository.ApprovalWorkflowRepository;
import com.smartprocure.repository.DepartmentRepository;
import com.smartprocure.repository.UserRepository;
import com.smartprocure.service.ApprovalChainService;
import com.smartprocure.service.ApprovalWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin management of approval workflows.
 *
 * Validation enforced here because it needs users, roles and the existing
 * workflow set:
 * 1. Names are unique, so workflows are identifiable in audit history.
 * 2. maxAmount, when present, must be >= minAmount.
 * 3. Every step must resolve to somebody with approval authority.
 * 4. At least one step, since a workflow with none would auto-approve.
 *
 * Overlapping amount bands are deliberately allowed — priority decides the
 * winner. That is the main advantage over independent per-level rules, where
 * an overlap left the chain order undefined.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalWorkflowServiceImpl implements ApprovalWorkflowService {

    private final ApprovalWorkflowRepository workflowRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ApprovalChainService approvalChainService;

    @Override
    @Transactional
    public ApprovalWorkflowResponse createWorkflow(ApprovalWorkflowRequest request) {
        log.info("Creating approval workflow '{}' [band={}-{}, department={}, steps={}]",
                request.getName(), request.getMinAmount(), request.getMaxAmount(),
                request.getDepartmentId(), request.getSteps().size());

        if (workflowRepository.existsByNameIgnoreCase(request.getName())) {
            log.error("Duplicate workflow name: {}", request.getName());
            throw new DuplicateResourceException("A workflow named \"" + request.getName() + "\" already exists.");
        }

        validateAmountBand(request);

        ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .name(request.getName())
                .description(request.getDescription())
                .minAmount(request.getMinAmount())
                .maxAmount(request.getMaxAmount())
                .department(resolveDepartment(request.getDepartmentId()))
                .priority(request.getPriority() == null ? 0 : request.getPriority())
                .active(request.getActive() == null || request.getActive())
                .build();

        applySteps(workflow, request);

        ApprovalWorkflow saved = workflowRepository.save(workflow);
        log.info("Approval workflow created: id={}, name='{}'", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ApprovalWorkflowResponse updateWorkflow(Long id, ApprovalWorkflowRequest request) {
        log.info("Updating approval workflow id: {}", id);
        ApprovalWorkflow workflow = getWorkflowOrThrow(id);

        workflowRepository.findByNameIgnoreCase(request.getName())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    log.error("Duplicate workflow name on update: {}", request.getName());
                    throw new DuplicateResourceException(
                            "A workflow named \"" + request.getName() + "\" already exists.");
                });

        validateAmountBand(request);

        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setMinAmount(request.getMinAmount());
        workflow.setMaxAmount(request.getMaxAmount());
        workflow.setDepartment(resolveDepartment(request.getDepartmentId()));
        if (request.getPriority() != null) workflow.setPriority(request.getPriority());
        if (request.getActive() != null) workflow.setActive(request.getActive());

        // Steps are replaced wholesale. Approvals already created from the old
        // steps are unaffected because they snapshot names and approvers.
        workflow.clearSteps();
        applySteps(workflow, request);

        ApprovalWorkflow updated = workflowRepository.save(workflow);
        log.info("Approval workflow updated: id={}, steps={}", updated.getId(), updated.getSteps().size());
        return toResponse(updated);
    }

    @Override
    @Transactional
    public ApprovalWorkflowResponse setActive(Long id, boolean active) {
        log.info("Setting approval workflow id: {} active={}", id, active);
        ApprovalWorkflow workflow = getWorkflowOrThrow(id);
        workflow.setActive(active);
        return toResponse(workflowRepository.save(workflow));
    }

    @Override
    @Transactional
    public void deleteWorkflow(Long id) {
        log.info("Deleting approval workflow id: {}", id);
        ApprovalWorkflow workflow = getWorkflowOrThrow(id);
        workflowRepository.delete(workflow);
        log.info("Approval workflow deleted: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalWorkflowResponse> getAllWorkflows() {
        log.info("Fetching all approval workflows");
        List<ApprovalWorkflowResponse> workflows = workflowRepository.findAllWithSteps().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        log.info("Found {} approval workflows", workflows.size());
        return workflows;
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalWorkflowResponse getWorkflowById(Long id) {
        log.info("Fetching approval workflow id: {}", id);
        return toResponse(getWorkflowOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalWorkflowResponse previewWorkflow(BigDecimal amount, Long departmentId) {
        log.info("Previewing workflow for amount {} and department {}", amount, departmentId);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Amount must be zero or greater.");
        }
        // Null when nothing matches, which the caller renders as the
        // unrouted-fallback warning.
        return approvalChainService.selectWorkflow(amount, departmentId)
                .map(this::toResponse)
                .orElse(null);
    }

    // ---------------------------------------------------------------------
    // Validation and mapping
    // ---------------------------------------------------------------------

    private void validateAmountBand(ApprovalWorkflowRequest request) {
        if (request.getMaxAmount() != null
                && request.getMaxAmount().compareTo(request.getMinAmount()) < 0) {
            log.error("Invalid amount band: min={} exceeds max={}", request.getMinAmount(), request.getMaxAmount());
            throw new BadRequestException("Maximum amount must be greater than or equal to minimum amount.");
        }
    }

    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) return null;
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> {
                    log.error("Department not found: id={}", departmentId);
                    return new ResourceNotFoundException("Department", "id", departmentId);
                });
    }

    /**
     * Builds the step list, normalising order to 1..n in the sequence given so
     * an admin never has to manage step numbers by hand.
     */
    private void applySteps(ApprovalWorkflow workflow, ApprovalWorkflowRequest request) {
        int order = 1;
        for (ApprovalWorkflowRequest.StepRequest stepRequest : request.getSteps()) {
            ApproverType type = parseApproverType(stepRequest.getApproverType());

            ApprovalWorkflowStep step = ApprovalWorkflowStep.builder()
                    .stepOrder(order++)
                    .name(stepRequest.getName())
                    .approverType(type)
                    .build();

            switch (type) {
                case SPECIFIC_USER -> {
                    if (stepRequest.getApproverId() == null) {
                        throw new BadRequestException(
                                "Step \"" + stepRequest.getName() + "\" needs an approver to be selected.");
                    }
                    step.setApprover(resolveApprover(stepRequest.getApproverId()));
                }
                case ROLE -> step.setApproverRole(parseApproverRole(stepRequest));
                case REQUESTER_MANAGER -> {
                    // Resolved per requisition at submit time; nothing to store.
                }
            }

            workflow.addStep(step);
        }
    }

    private ApproverType parseApproverType(String raw) {
        try {
            return ApproverType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid approver type: {}", raw);
            throw new BadRequestException(
                    "Invalid approver type: " + raw + ". Expected SPECIFIC_USER, ROLE or REQUESTER_MANAGER.");
        }
    }

    /**
     * Only APPROVER and ADMIN may be targeted. Pointing a step at BUYER would
     * hand approval authority to the people raising the requests.
     */
    private RoleName parseApproverRole(ApprovalWorkflowRequest.StepRequest stepRequest) {
        if (stepRequest.getApproverRole() == null || stepRequest.getApproverRole().isBlank()) {
            throw new BadRequestException(
                    "Step \"" + stepRequest.getName() + "\" needs a role to be selected.");
        }
        RoleName role;
        try {
            role = RoleName.valueOf(stepRequest.getApproverRole().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid approver role: {}", stepRequest.getApproverRole());
            throw new BadRequestException("Invalid role: " + stepRequest.getApproverRole());
        }
        if (role != RoleName.APPROVER && role != RoleName.ADMIN) {
            log.error("Role {} cannot be used as an approver role", role);
            throw new BusinessRuleException(
                    "Only the APPROVER and ADMIN roles can be used for approval steps.");
        }
        return role;
    }

    /**
     * A step pointing at someone without authority, or at a deactivated user,
     * would silently stall every requisition it routes.
     */
    private User resolveApprover(Long approverId) {
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> {
                    log.error("Approver not found: id={}", approverId);
                    return new ResourceNotFoundException("User", "id", approverId);
                });

        boolean canApprove = approver.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.APPROVER || r.getName() == RoleName.ADMIN);

        if (!canApprove) {
            log.error("User {} lacks approval authority and cannot be used in a workflow step", approver.getEmail());
            throw new BusinessRuleException(
                    "User " + approver.getEmail() + " does not have the APPROVER or ADMIN role "
                            + "and cannot be assigned as an approver.");
        }
        if (approver.getStatus() != User.UserStatus.ACTIVE) {
            log.error("User {} is inactive and cannot be used in a workflow step", approver.getEmail());
            throw new BusinessRuleException(
                    "User " + approver.getEmail() + " is inactive and cannot be assigned as an approver.");
        }
        return approver;
    }

    private ApprovalWorkflow getWorkflowOrThrow(Long id) {
        return workflowRepository.findByIdWithSteps(id)
                .orElseThrow(() -> {
                    log.error("Approval workflow not found: id={}", id);
                    return new ResourceNotFoundException("ApprovalWorkflow", "id", id);
                });
    }

    private ApprovalWorkflowResponse toResponse(ApprovalWorkflow workflow) {
        List<StepResponse> steps = workflow.getSteps().stream()
                .sorted((a, b) -> Integer.compare(a.getStepOrder(), b.getStepOrder()))
                .map(this::toStepResponse)
                .collect(Collectors.toList());

        return ApprovalWorkflowResponse.builder()
                .id(workflow.getId())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .minAmount(workflow.getMinAmount())
                .maxAmount(workflow.getMaxAmount())
                .departmentId(workflow.getDepartment() != null ? workflow.getDepartment().getId() : null)
                .departmentName(workflow.getDepartment() != null ? workflow.getDepartment().getName() : null)
                .priority(workflow.getPriority())
                .active(workflow.isActive())
                .steps(steps)
                .createdAt(workflow.getCreatedAt())
                .updatedAt(workflow.getUpdatedAt())
                .build();
    }

    private StepResponse toStepResponse(ApprovalWorkflowStep step) {
        User approver = step.getApprover();
        return StepResponse.builder()
                .id(step.getId())
                .stepOrder(step.getStepOrder())
                .name(step.getName())
                .approverType(step.getApproverType().name())
                .approverId(approver != null ? approver.getId() : null)
                .approverName(approver != null
                        ? approver.getFirstName() + " " + approver.getLastName() : null)
                .approverEmail(approver != null ? approver.getEmail() : null)
                .approverRole(step.getApproverRole() != null ? step.getApproverRole().name() : null)
                .approverLabel(describeApprover(step))
                .build();
    }

    /** Single readable summary so the UI does not repeat this branching. */
    private String describeApprover(ApprovalWorkflowStep step) {
        return switch (step.getApproverType()) {
            case SPECIFIC_USER -> step.getApprover() != null
                    ? step.getApprover().getFirstName() + " " + step.getApprover().getLastName()
                    : "Unassigned";
            case ROLE -> "Any " + step.getApproverRole();
            case REQUESTER_MANAGER -> "Requester's manager";
        };
    }
}
