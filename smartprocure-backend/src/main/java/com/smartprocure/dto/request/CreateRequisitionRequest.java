package com.smartprocure.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateRequisitionRequest {

    @Size(max = 1000)
    private String description;

    private Long departmentId;

    private Long costCenterId;

    /**
     * @Valid cascades validation to each item in the list.
     * @NotEmpty ensures at least one item is required.
     */
    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<RequisitionItemRequest> items;
}
