package com.smartprocure.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class ChangeOrderRequest {

    private LocalDate expectedDeliveryDate;

    @Size(max = 100, message = "Payment terms must not exceed 100 characters")
    private String paymentTerms;

    @Size(max = 100, message = "Shipping method must not exceed 100 characters")
    private String shippingMethod;

    @NotBlank(message = "Change reason is required")
    @Size(max = 2000, message = "Change reason must not exceed 2000 characters")
    private String changeReason;

    @Valid
    private List<LineItemChange> items;

    @Getter
    @Setter
    public static class LineItemChange {
        private Long id; // existing item id, null for new
        private String itemName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
