package com.smartprocure.mapper;

import com.smartprocure.dto.response.ApprovalResponse;
import com.smartprocure.entity.Approval;
import org.springframework.stereotype.Component;

@Component
public class ApprovalMapper {

    public ApprovalResponse toResponse(Approval approval) {
        if (approval == null) return null;

        Approval.ApprovalStatus status = approval.getStatus();
        var req = approval.getRequisition();

        return ApprovalResponse.builder()
                .id(approval.getId())
                .requisitionId(req.getId())
                .requisitionNumber(req.getRequisitionNumber())
                .requesterName(req.getRequester().getFirstName() + " " + req.getRequester().getLastName())
                .approverId(approval.getApprover() != null ? approval.getApprover().getId() : null)
                .approverName(approval.getApprover() != null
                        ? approval.getApprover().getFirstName() + " " + approval.getApprover().getLastName()
                        : null)
                .assignedApproverId(approval.getAssignedApprover() != null
                        ? approval.getAssignedApprover().getId() : null)
                .assignedApproverName(approval.getAssignedApprover() != null
                        ? approval.getAssignedApprover().getFirstName() + " " + approval.getAssignedApprover().getLastName()
                        : null)
                .assignedApproverEmail(approval.getAssignedApprover() != null
                        ? approval.getAssignedApprover().getEmail() : null)
                .assignedRole(approval.getAssignedRole() != null
                        ? approval.getAssignedRole().name() : null)
                .ruleName(approval.getRuleName())
                .workflowName(approval.getWorkflowName())
                .approvalLevel(approval.getApprovalLevel())
                .status(status.name())
                .comments(approval.getComments())
                .approvedAt(approval.getApprovedAt())
                .createdAt(approval.getCreatedAt())
                .departmentName(req.getDepartment() != null ? req.getDepartment().getName() : null)
                .totalAmount(req.getTotalAmount())
                .build();
    }
}
