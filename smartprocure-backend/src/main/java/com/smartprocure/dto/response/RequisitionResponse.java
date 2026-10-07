package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class RequisitionResponse {
    private Long id;
    private String requisitionNumber;
    private String requesterName;
    private Long requesterId;
    private String departmentName;
    private Long departmentId;
    private String costCenterName;
    private Long costCenterId;
    private String description;
    private BigDecimal totalAmount;
    private String status;
    private List<RequisitionItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
