package com.smartprocure.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One step in a requisition's approval chain.
 *
 * When a requisition is submitted, an Approval row is created for every level
 * matched by the configured {@link ApprovalRule}s. The first level starts as
 * PENDING; later levels start as WAITING and are promoted to PENDING one at a
 * time as the preceding level approves. This makes the whole chain visible up
 * front while still enforcing strict sequential approval.
 *
 * Terminal outcomes:
 * - APPROVED: this level signed off.
 * - REJECTED: this level refused; the requisition stops here.
 * - SKIPPED:  a later level that never got its turn because an earlier
 *             level rejected.
 */
@Entity
@Table(name = "approvals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The requisition this approval belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requisition_id", nullable = false)
    private Requisition requisition;

    /**
     * The user who actually approved/rejected.
     * Null until this level is actioned.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    /**
     * The individual expected to action this level, resolved from the
     * workflow step at submission time. Only this user (or an ADMIN) may act.
     *
     * Null when the step is role-based (see {@link #assignedRole}) or when no
     * workflow matched at all.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_approver_id")
    private User assignedApprover;

    /**
     * Set instead of {@link #assignedApprover} for role-based steps: any
     * active holder of this role may action the level.
     *
     * When both this and assignedApprover are null, the level is open to any
     * approver — the fallback for an unconfigured system.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", length = 30)
    private Role.RoleName assignedRole;

    /**
     * Position in the chain. Levels are actioned in ascending order.
     */
    @Column(name = "approval_level", nullable = false)
    @Builder.Default
    private Integer approvalLevel = 1;

    /**
     * Step label, snapshotted at submission time.
     *
     * The workflow name and step label are copied rather than referenced so
     * that editing or deleting a workflow never rewrites the history of
     * requisitions already in flight.
     */
    @Column(name = "rule_name", length = 150)
    private String ruleName;

    /** Name of the workflow that produced this chain, snapshotted. */
    @Column(name = "workflow_name", length = 150)
    private String workflowName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    /**
     * Comments provided by the approver — required on rejection.
     */
    @Column(name = "comments", length = 1000)
    private String comments;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ApprovalStatus {
        /** Queued behind an earlier level; not yet actionable. */
        WAITING,
        /** Actionable now. */
        PENDING,
        APPROVED,
        REJECTED,
        /** Never reached because an earlier level rejected. */
        SKIPPED
    }
}
