package com.smartprocure.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Create/update payload for an approval workflow and its ordered steps.
 * Cross-field and per-step semantic checks live in the service, which has
 * access to users, roles and the existing workflow set.
 */
@Getter
@Setter
public class ApprovalWorkflowRequest {

    @NotBlank(message = "Workflow name is required")
    @Size(max = 150, message = "Workflow name must not exceed 150 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull(message = "Minimum amount is required")
    @DecimalMin(value = "0.0", message = "Minimum amount cannot be negative")
    private BigDecimal minAmount;

    /** Null means no upper limit. */
    @DecimalMin(value = "0.0", message = "Maximum amount cannot be negative")
    private BigDecimal maxAmount;

    /** Null means the workflow applies to all departments. */
    private Long departmentId;

    /** Higher priority wins when multiple workflows match. */
    @Min(value = 0, message = "Priority cannot be negative")
    private Integer priority;

    private Boolean active;

    @NotEmpty(message = "A workflow must have at least one approval step")
    @Size(max = 10, message = "A workflow cannot have more than 10 steps")
    @Valid
    private List<StepRequest> steps;

    @Getter
    @Setter
    public static class StepRequest {

        @NotBlank(message = "Step name is required")
        @Size(max = 150, message = "Step name must not exceed 150 characters")
        private String name;

        /** SPECIFIC_USER, ROLE or REQUESTER_MANAGER. */
        @NotBlank(message = "Approver type is required")
        private String approverType;

        /** Required when approverType is SPECIFIC_USER. */
        private Long approverId;

        /** Required when approverType is ROLE. */
        private String approverRole;
    }
}
