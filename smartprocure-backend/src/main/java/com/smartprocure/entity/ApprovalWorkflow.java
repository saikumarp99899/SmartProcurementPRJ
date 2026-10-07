package com.smartprocure.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A named, ordered approval route that requisitions are matched against.
 *
 * A workflow owns its steps, so the chain is explicit rather than inferred by
 * grouping independent rules. That removes the ambiguity you get when two
 * separately-defined levels claim overlapping amount ranges.
 *
 * Example:
 *   Workflow "High Value IT Purchase"
 *     When:  50,001 - 5,00,000, department = IT
 *     Steps: 1. Requester's Manager
 *            2. Finance Head (specific user)
 *            3. Any APPROVER (role)
 *
 * Overlapping workflows are permitted on purpose — {@code priority} decides
 * which one wins, which is more flexible than forbidding overlap outright.
 */
@Entity
@Table(name = "approval_workflows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    // ------------------------------------------------------------------
    // Matching criteria
    // ------------------------------------------------------------------

    /** Inclusive lower bound of the amount band. */
    @Column(name = "min_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal minAmount;

    /** Inclusive upper bound; NULL means no upper limit. */
    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    /**
     * Restricts this workflow to one department.
     * NULL means it applies to every department.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /**
     * Tie-breaker when several workflows match the same requisition.
     * Higher wins. Lets an admin add a narrow override without having to
     * carve holes out of the broader workflow's amount band.
     */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    // ------------------------------------------------------------------
    // Steps
    // ------------------------------------------------------------------

    /**
     * Ordered steps. Cascade + orphanRemoval means editing a workflow's steps
     * is a single save, and removed steps are deleted rather than orphaned.
     */
    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    @Builder.Default
    private List<ApprovalWorkflowStep> steps = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Keeps both sides of the relationship consistent. */
    public void addStep(ApprovalWorkflowStep step) {
        steps.add(step);
        step.setWorkflow(this);
    }

    public void clearSteps() {
        steps.forEach(s -> s.setWorkflow(null));
        steps.clear();
    }

    public boolean matchesAmount(BigDecimal amount) {
        if (amount == null) return false;
        if (amount.compareTo(minAmount) < 0) return false;
        return maxAmount == null || amount.compareTo(maxAmount) <= 0;
    }

    /** A department-specific workflow is considered more specific than a global one. */
    public boolean isDepartmentSpecific() {
        return department != null;
    }
}
