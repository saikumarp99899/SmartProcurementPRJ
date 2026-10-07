package com.smartprocure.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RequisitionItemRequest {

    @NotBlank(message = "Item name is required")
    @Size(max = 200)
    private String itemName;

    @Size(max = 500)
    private String description;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than 0")
    private Integer quantity;

    @NotNull(message = "Unit price is required")
    @Positive(message = "Unit price must be greater than 0")
    private BigDecimal unitPrice;
}
