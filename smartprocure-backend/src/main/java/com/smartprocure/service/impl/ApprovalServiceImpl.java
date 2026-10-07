package com.smartprocure.service.impl;

import com.smartprocure.audit.AuditLogService;
import com.smartprocure.dto.request.ApprovalActionRequest;
import com.smartprocure.dto.response.ApprovalResponse;
import com.smartprocure.dto.response.PageResponse;
import com.smartprocure.entity.Approval;
import com.smartprocure.entity.Approval.ApprovalStatus;
import com.smartprocure.entity.AuditLog.AuditAction;
import com.smartprocure.entity.Requisition;
import com.smartprocure.entity.Requisition.RequisitionStatus;
import com.smartprocure.entity.Role;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.entity.User;
import com.smartprocure.exception.BadRequestException;
import com.smartprocure.exception.BusinessRuleException;
import com.smartprocure.exception.ForbiddenException;
import com.smartprocure.exception.ResourceNotFoundException;
import com.smartprocure.mapper.ApprovalMapper;
import com.smartprocure.repository.ApprovalRepository;
import com.smartprocure.repository.RequisitionRepository;
import com.smartprocure.repository.UserRepository;
import com.smartprocure.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final RequisitionRepository requisitionRepository;
    private final UserRepository userRepository;
    private final ApprovalMapper approvalMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public ApprovalResponse approveRequisition(Long approvalId, ApprovalActionRequest request, Long approverId) {
        log.info("Approving approval id: {} by user id: {}", approvalId, approverId);
        Approval approval = getApprovalOrThrow(approvalId);
        User approver = getUserOrThrow(approverId);

        validateActionable(approval, approver, "approve");

        approval.setApprover(approver);
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setComments(request.getComments());
        approval.setApprovedAt(LocalDateTime.now());
        Approval saved = approvalRepository.save(approval);

        Requisition req = approval.getRequisition();

        // Advance the chain: promote the next queued level, if there is one.
        Optional<Approval> nextLevel = approvalRepository
                .findFirstByRequisitionIdAndStatusAndApprovalLevelGreaterThanOrderByApprovalLevelAsc(
                        req.getId(), ApprovalStatus.WAITING, approval.getApprovalLevel());

        if (nextLevel.isPresent()) {
            Approval next = nextLevel.get();
            next.setStatus(ApprovalStatus.PENDING);
            approvalRepository.save(next);

            // Requisition stays SUBMITTED — it is not fully approved yet.
            log.info("Requisition {} approved at level {} by {}; advanced to level {} assigned to {}",
                    req.getRequisitionNumber(), approval.getApprovalLevel(), approver.getEmail(),
                    next.getApprovalLevel(),
                    next.getAssignedApprover() != null ? next.getAssignedApprover().getEmail() : "any approver");

            auditLogService.log(approver, AuditAction.REQUISITION_APPROVED, "REQUISITION", req.getId(),
                    "Requisition " + req.getRequisitionNumber() + " approved at level "
                            + approval.getApprovalLevel() + " by " + approver.getEmail()
                            + "; awaiting level " + next.getApprovalLevel());
        } else {
            // Final level signed off — the requisition is now fully approved.
            req.setStatus(RequisitionStatus.APPROVED);
            requisitionRepository.save(req);

            log.info("Requisition {} fully approved at final level {} by {}",
                    req.getRequisitionNumber(), approval.getApprovalLevel(), approver.getEmail());

            auditLogService.log(approver, AuditAction.REQUISITION_APPROVED, "REQUISITION", req.getId(),
                    "Requisition " + req.getRequisitionNumber() + " fully approved by " + approver.getEmail());
        }

        return approvalMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ApprovalResponse rejectRequisition(Long approvalId, ApprovalActionRequest request, Long approverId) {
        log.info("Rejecting approval id: {} by user id: {}", approvalId, approverId);
        Approval approval = getApprovalOrThrow(approvalId);
        User approver = getUserOrThrow(approverId);

        validateActionable(approval, approver, "reject");

        // Business rule: comments are required for rejection
        if (request.getComments() == null || request.getComments().isBlank()) {
            log.error("Rejection attempted without comments for approval id: {}", approvalId);
            throw new BadRequestException("Comments are required when rejecting a requisition.");
        }

        approval.setApprover(approver);
        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setComments(request.getComments());
        approval.setApprovedAt(LocalDateTime.now());
        Approval saved = approvalRepository.save(approval);

        Requisition req = approval.getRequisition();
        req.setStatus(RequisitionStatus.REJECTED);
        requisitionRepository.save(req);

        // A rejection ends the chain: later levels never get their turn.
        List<Approval> queued = approvalRepository.findByRequisitionIdAndStatusIn(
                req.getId(), List.of(ApprovalStatus.WAITING));
        for (Approval waiting : queued) {
            waiting.setStatus(ApprovalStatus.SKIPPED);
            waiting.setComments("Skipped — rejected at level " + approval.getApprovalLevel());
            approvalRepository.save(waiting);
        }
        if (!queued.isEmpty()) {
            log.info("Skipped {} queued approval level(s) for requisition {} after rejection",
                    queued.size(), req.getRequisitionNumber());
        }

        log.info("Requisition {} rejected at level {} by {}",
                req.getRequisitionNumber(), approval.getApprovalLevel(), approver.getEmail());

        auditLogService.log(approver, AuditAction.REQUISITION_REJECTED,
                "REQUISITION", req.getId(),
                "Requisition " + req.getRequisitionNumber() + " rejected at level "
                        + approval.getApprovalLevel() + " by " + approver.getEmail()
                        + ". Reason: " + request.getComments());

        return approvalMapper.toResponse(saved);
    }

    /**
     * Guards a level against out-of-turn and unauthorised action.
     *
     * WAITING is called out separately from the other non-PENDING states
     * because it is the one case where the request is legitimate but simply
     * premature, and the approver benefits from being told so explicitly.
     *
     * Assignment is enforced so that a rule naming a specific approver
     * actually means something. ADMINs may act on any level as a deliberate
     * override for when an assigned approver is unavailable.
     */
    private void validateActionable(Approval approval, User actor, String action) {
        if (approval.getStatus() == ApprovalStatus.WAITING) {
            log.error("Out-of-turn {} attempt on level {} of requisition {} (still WAITING)",
                    action, approval.getApprovalLevel(),
                    approval.getRequisition().getRequisitionNumber());
            throw new BusinessRuleException(
                    "Level " + approval.getApprovalLevel() + " is not yet actionable. "
                            + "Earlier approval levels must be completed first.");
        }

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            log.error("Cannot {} approval id {} with status {}", action, approval.getId(), approval.getStatus());
            throw new BusinessRuleException(
                    "This approval is already " + approval.getStatus() + " and cannot be " + action + "d.");
        }

        Requisition requisition = approval.getRequisition();
        boolean isAdmin = actor.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN);

        // Separation of duties: nobody signs off their own request. Checked
        // even for admins, since an admin raising a requisition is still the
        // requester. Chain building already skips named steps that resolve to
        // the requester; this catches role-based steps, where other holders
        // remain able to act.
        if (requisition.getRequester().getId().equals(actor.getId())) {
            log.error("Self-approval blocked: {} attempted to {} their own requisition {}",
                    actor.getEmail(), action, requisition.getRequisitionNumber());
            throw new ForbiddenException(
                    "You cannot " + action + " your own requisition.");
        }

        User assigned = approval.getAssignedApprover();
        if (assigned != null) {
            if (!assigned.getId().equals(actor.getId()) && !isAdmin) {
                log.error("Unauthorised {} attempt: user {} is not the assigned approver ({}) for level {} of {}",
                        action, actor.getEmail(), assigned.getEmail(), approval.getApprovalLevel(),
                        requisition.getRequisitionNumber());
                throw new ForbiddenException(
                        "This approval is assigned to " + assigned.getEmail()
                                + ". You are not authorised to " + action + " it.");
            }
            return;
        }

        // Role-based step: any active holder of that role may act.
        RoleName requiredRole = approval.getAssignedRole();
        if (requiredRole != null) {
            boolean holdsRole = actor.getRoles().stream().anyMatch(r -> r.getName() == requiredRole);
            if (!holdsRole && !isAdmin) {
                log.error("Unauthorised {} attempt: user {} lacks required role {} for level {} of {}",
                        action, actor.getEmail(), requiredRole, approval.getApprovalLevel(),
                        requisition.getRequisitionNumber());
                throw new ForbiddenException(
                        "This approval requires the " + requiredRole + " role. "
                                + "You are not authorised to " + action + " it.");
            }
        }

        // Neither an individual nor a role assigned: open to any approver,
        // which is the unrouted fallback.
    }

    /**
     * Pending approvals for the current user.
     *
     * Approvers see only levels assigned to them (plus unassigned ones);
     * showing them levels they cannot action would be misleading. ADMINs see
     * everything, consistent with their override ability.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApprovalResponse> getPendingApprovals(Long currentUserId, Pageable pageable) {
        User actor = getUserOrThrow(currentUserId);
        boolean isAdmin = actor.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN);

        log.info("Fetching pending approvals for user id: {} (email: {}) [admin={}, roles={}, page={}, size={}]",
                currentUserId, actor.getEmail(), isAdmin,
                actor.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.joining(",")),
                pageable.getPageNumber(), pageable.getPageSize());

        Page<Approval> page;
        if (isAdmin) {
            page = approvalRepository.findByStatus(ApprovalStatus.PENDING, pageable);
        } else {
            Collection<RoleName> roles = roleNamesOf(actor);
            log.debug("Querying actionable approvals with status=PENDING, approverId={}, roles={}",
                    currentUserId, roles);
            page = approvalRepository.findActionableByApprover(
                    ApprovalStatus.PENDING, currentUserId, roles, pageable);
        }

        log.info("Found {} pending approvals actionable by user id: {} ({})",
                page.getTotalElements(), currentUserId, actor.getEmail());

        List<ApprovalResponse> data = page.getContent().stream()
                .map(approvalMapper::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<ApprovalResponse>builder()
                .data(data)
                .currentPage(page.getNumber())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageSize(page.getSize())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalResponse> getApprovalsByRequisition(Long requisitionId) {
        // Validate the requisition exists
        if (!requisitionRepository.existsById(requisitionId)) {
            throw new ResourceNotFoundException("Requisition", "id", requisitionId);
        }
        // Chain order, so the UI can render the route as configured.
        return approvalRepository.findByRequisitionIdOrderByApprovalLevelAsc(requisitionId).stream()
                .map(approvalMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalResponse getApprovalById(Long id) {
        log.info("Fetching approval by id: {}", id);
        return approvalMapper.toResponse(getApprovalOrThrow(id));
    }

    /**
     * Role names held by a user. Never empty in practice, but an empty
     * collection would break an SQL IN clause, so a placeholder is returned.
     */
    private Collection<RoleName> roleNamesOf(User user) {
        Set<RoleName> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return roles.isEmpty() ? Set.of(RoleName.VENDOR) : roles;
    }

    private Approval getApprovalOrThrow(Long id) {
        return approvalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval", "id", id));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }
}
