package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApprovalResponse {
    private Long id;
    private Long requisitionId;
    private String requisitionNumber;
    private String requesterName;
    /** Who actually actioned this level (null until actioned). */
    private Long approverId;
    private String approverName;
    /** Who is expected to action it (null = any approver). */
    private Long assignedApproverId;
    private String assignedApproverName;
    private String assignedApproverEmail;
    /** Set instead of an individual when the step targets a role. */
    private String assignedRole;
    /** Step label, snapshotted from the workflow. */
    private String ruleName;
    /** Workflow that produced this chain, snapshotted. */
    private String workflowName;
    private Integer approvalLevel;
    private String status;
    private String comments;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    // Summary info from the requisition
    private String departmentName;
    private java.math.BigDecimal totalAmount;
}
