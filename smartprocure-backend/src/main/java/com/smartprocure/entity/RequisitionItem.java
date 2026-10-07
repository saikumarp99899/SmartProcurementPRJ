package com.smartprocure.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a single line item inside a Requisition.
 * Each item has a name, quantity, unit price. Total price is calculated on backend.
 */
@Entity
@Table(name = "requisition_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequisitionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Back-reference to the parent Requisition.
     * FetchType.LAZY: only loaded when accessed — prevents accidental loading.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requisition_id", nullable = false)
    private Requisition requisition;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /**
     * totalPrice = quantity * unitPrice
     * Always calculated on backend. Never accept from frontend.
     */
    @Column(name = "total_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrice;

    /**
     * Called from service layer to compute totalPrice safely.
     */
    public void calculateTotalPrice() {
        if (quantity != null && unitPrice != null) {
            this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
