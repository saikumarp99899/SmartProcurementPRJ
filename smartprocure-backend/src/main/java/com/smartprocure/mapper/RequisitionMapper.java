package com.smartprocure.mapper;

import com.smartprocure.dto.response.RequisitionItemResponse;
import com.smartprocure.dto.response.RequisitionResponse;
import com.smartprocure.entity.Requisition;
import com.smartprocure.entity.RequisitionItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RequisitionMapper {

    public RequisitionResponse toResponse(Requisition req) {
        if (req == null) return null;

        List<RequisitionItemResponse> itemResponses = req.getItems().stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        return RequisitionResponse.builder()
                .id(req.getId())
                .requisitionNumber(req.getRequisitionNumber())
                .requesterId(req.getRequester().getId())
                .requesterName(req.getRequester().getFirstName() + " " + req.getRequester().getLastName())
                .departmentId(req.getDepartment() != null ? req.getDepartment().getId() : null)
                .departmentName(req.getDepartment() != null ? req.getDepartment().getName() : null)
                .costCenterId(req.getCostCenter() != null ? req.getCostCenter().getId() : null)
                .costCenterName(req.getCostCenter() != null ? req.getCostCenter().getName() : null)
                .description(req.getDescription())
                .totalAmount(req.getTotalAmount())
                .status(req.getStatus().name())
                .items(itemResponses)
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
                .build();
    }

    public RequisitionItemResponse toItemResponse(RequisitionItem item) {
        return RequisitionItemResponse.builder()
                .id(item.getId())
                .itemName(item.getItemName())
                .description(item.getDescription())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .build();
    }
}
