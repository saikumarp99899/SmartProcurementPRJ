package com.smartprocure.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LEGACY — superseded by {@link ApprovalWorkflow} and its steps.
 *
 * Retained only so {@code ApprovalRuleMigrator} can convert existing rows
 * into workflows on startup; nothing else reads this table. Do not add new
 * behaviour here. Once every environment has migrated, this entity, its
 * repository and the approval_rules table can be dropped.
 *
 * The original design below was a flat list where rules sharing an amount
 * band implied a chain. Workflows replaced it because that made chain order
 * undefined whenever two bands overlapped.
 *
 * Example configuration:
 *   Level 1 | 0        - 50,000    | Team Manager
 *   Level 1 | 50,001   - 500,000   | Team Manager
 *   Level 2 | 50,001   - 500,000   | Finance Head
 *   Level 1 | 500,001  - unlimited | Team Manager
 *   Level 2 | 500,001  - unlimited | Finance Head
 *   Level 3 | 500,001  - unlimited | Procurement Head
 *
 * A requisition of 120,000 therefore needs Manager then Finance Head,
 * in that order. A requisition of 10,000 needs only the Manager.
 */
@Entity
@Table(name = "approval_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable label, e.g. "Finance Head sign-off". */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /**
     * Position in the approval chain. Levels are actioned in ascending order:
     * level 2 only becomes actionable once level 1 has approved.
     */
    @Column(name = "approval_level", nullable = false)
    private Integer approvalLevel;

    /** Inclusive lower bound of the amount band this rule applies to. */
    @Column(name = "min_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal minAmount;

    /**
     * Inclusive upper bound. NULL means "no upper limit",
     * which is how the highest band is expressed.
     */
    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    /**
     * The specific user who must approve at this level.
     * Assigning a concrete person (rather than just a role) is what makes
     * accountability traceable — the requester can see exactly who holds it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    /**
     * Inactive rules are ignored when building an approval chain.
     * Deactivating is preferred over deleting so historical approvals
     * created from this rule keep their meaning.
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * True when the given amount falls inside this rule's band.
     * Kept on the entity so the matching logic lives with the data it describes.
     */
    public boolean matchesAmount(BigDecimal amount) {
        if (amount == null) return false;
        if (amount.compareTo(minAmount) < 0) return false;
        return maxAmount == null || amount.compareTo(maxAmount) <= 0;
    }
}
