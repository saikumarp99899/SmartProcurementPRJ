package com.smartprocure.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One step in an {@link ApprovalWorkflow}.
 *
 * The approver is described rather than always named outright. Naming a
 * specific person is simple but brittle: every org change means editing
 * workflows, and one person's absence blocks everything in that band.
 * ROLE and REQUESTER_MANAGER keep the workflow stable as people change.
 */
@Entity
@Table(name = "approval_workflow_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalWorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private ApprovalWorkflow workflow;

    /** 1-based position. Steps are actioned in ascending order. */
    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    /** Label shown to approvers and requesters, e.g. "Finance sign-off". */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_type", nullable = false, length = 30)
    private ApproverType approverType;

    /** Required when approverType is SPECIFIC_USER; ignored otherwise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    /** Required when approverType is ROLE; ignored otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(name = "approver_role", length = 30)
    private Role.RoleName approverRole;

    public enum ApproverType {
        /** A named individual. */
        SPECIFIC_USER,
        /** Any active holder of a role may action the step. */
        ROLE,
        /** Resolved at submit time from the requester's manager. */
        REQUESTER_MANAGER
    }
}
