package com.smartprocure.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Purchase Order issued to a Vendor.
 *
 * Business Rule: A PO can only be created from an APPROVED requisition.
 * The requisition status then moves to PO_CREATED.
 *
 * PO lifecycle: DRAFT → SENT → ACCEPTED → DELIVERED → CLOSED
 * Can also be CANCELLED at any point before DELIVERED.
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique system-generated PO number like PO-2024-0001
     */
    @Column(name = "po_number", nullable = false, unique = true, length = 50)
    private String poNumber;

    /**
     * The APPROVED requisition that triggered this PO.
     * One PO per requisition (enforced by business logic in service layer).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requisition_id", nullable = false, unique = true)
    private Requisition requisition;

    /**
     * The vendor to whom this PO is sent. Must be ACTIVE.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    /**
     * The user (BUYER) who created this PO.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    /**
     * Ship-to address for delivery.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_to_address_id")
    private CompanyAddress shipToAddress;

    /**
     * Bill-to address for invoicing.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_to_address_id")
    private CompanyAddress billToAddress;

    @Column(name = "payment_terms", length = 100)
    @Builder.Default
    private String paymentTerms = "30 Days Net";

    @Column(name = "shipping_method", length = 100)
    private String shippingMethod;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Subtotal of line items before tax and shipping.
     */
    @Column(name = "subtotal", precision = 15, scale = 2)
    private BigDecimal subtotal;

    /**
     * Tax amount calculated on the subtotal.
     */
    @Column(name = "tax_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /**
     * Shipping cost for the order.
     */
    @Column(name = "shipping_cost", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal shippingCost = BigDecimal.ZERO;

    /**
     * Calculated from PO line items + tax + shipping. Never trusted from frontend.
     */
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum PurchaseOrderStatus {
        DRAFT, SENT, ACCEPTED, DELIVERED, CLOSED, CANCELLED
    }

    public void addItem(PurchaseOrderItem item) {
        items.add(item);
        item.setPurchaseOrder(this);
    }

    public void recalculateTotalAmount() {
        this.subtotal = items.stream()
                .map(PurchaseOrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = this.taxAmount != null ? this.taxAmount : BigDecimal.ZERO;
        BigDecimal shipping = this.shippingCost != null ? this.shippingCost : BigDecimal.ZERO;
        this.totalAmount = this.subtotal.add(tax).add(shipping);
    }
}
