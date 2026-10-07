package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ApprovalWorkflowResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Long departmentId;
    private String departmentName;
    private Integer priority;
    private boolean active;
    private List<StepResponse> steps;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class StepResponse {
        private Long id;
        private Integer stepOrder;
        private String name;
        private String approverType;
        private Long approverId;
        private String approverName;
        private String approverEmail;
        private String approverRole;
        /** Human-readable summary of who will approve, for display. */
        private String approverLabel;
    }
}
