package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PurchaseOrderResponse {
    private Long id;
    private String poNumber;
    private Long requisitionId;
    private String requisitionNumber;
    private Long vendorId;
    private String vendorName;
    private String createdByName;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private BigDecimal totalAmount;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal shippingCost;
    private String paymentTerms;
    private String shippingMethod;
    private String notes;
    private String status;
    private List<PurchaseOrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
