package com.smartprocure.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CreatePurchaseOrderRequest {

    @NotNull(message = "Requisition ID is required")
    private Long requisitionId;

    @NotNull(message = "Vendor ID is required")
    private Long vendorId;

    @Future(message = "Expected delivery date must be in the future")
    private LocalDate expectedDeliveryDate;

    private Long shipToAddressId;

    private Long billToAddressId;

    @Size(max = 100, message = "Payment terms must not exceed 100 characters")
    private String paymentTerms;

    @Size(max = 100, message = "Shipping method must not exceed 100 characters")
    private String shippingMethod;

    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;

    private BigDecimal taxAmount;

    private BigDecimal shippingCost;

    /**
     * Optional line item overrides. If provided, these replace the
     * default items copied from the requisition.
     */
    @Valid
    private List<LineItemOverride> items;

    @Getter
    @Setter
    public static class LineItemOverride {
        private String itemName;
        private String description;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
