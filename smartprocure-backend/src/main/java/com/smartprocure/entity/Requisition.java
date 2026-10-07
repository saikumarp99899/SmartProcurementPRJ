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
 * Represents an internal procurement request raised by a Buyer.
 * A requisition goes through a lifecycle: DRAFT → SUBMITTED → APPROVED/REJECTED → PO_CREATED → COMPLETED
 */
@Entity
@Table(name = "requisitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Requisition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * System-generated unique number like REQ-2024-0001
     */
    @Column(name = "requisition_number", nullable = false, unique = true, length = 50)
    private String requisitionNumber;

    /**
     * The user who created this requisition (BUYER role)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    /**
     * The department this requisition belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /**
     * The cost center to which the expense is charged
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id")
    private CostCenter costCenter;

    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Calculated total — sum of all line item totals.
     * Always calculated on backend, never trusted from frontend.
     */
    @Column(name = "total_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private RequisitionStatus status = RequisitionStatus.DRAFT;

    /**
     * Line items for this requisition. CascadeType.ALL means items are saved/deleted with the requisition.
     * orphanRemoval = true means if you remove an item from the list, it gets deleted from DB.
     */
    @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RequisitionItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum RequisitionStatus {
        DRAFT, SUBMITTED, APPROVED, REJECTED, PO_CREATED, COMPLETED
    }

    /**
     * Helper method to add an item and set the back-reference.
     * This is important for bidirectional consistency.
     */
    public void addItem(RequisitionItem item) {
        items.add(item);
        item.setRequisition(this);
    }

    /**
     * Recalculates totalAmount from all items.
     * Called from service layer before saving.
     */
    public void recalculateTotalAmount() {
        this.totalAmount = items.stream()
                .map(RequisitionItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
