package com.smartprocure.mapper;

import com.smartprocure.dto.response.PurchaseOrderItemResponse;
import com.smartprocure.dto.response.PurchaseOrderResponse;
import com.smartprocure.entity.PurchaseOrder;
import com.smartprocure.entity.PurchaseOrderItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PurchaseOrderMapper {

    public PurchaseOrderResponse toResponse(PurchaseOrder po) {
        if (po == null) return null;

        List<PurchaseOrderItemResponse> itemResponses = po.getItems().stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        return PurchaseOrderResponse.builder()
                .id(po.getId())
                .poNumber(po.getPoNumber())
                .requisitionId(po.getRequisition().getId())
                .requisitionNumber(po.getRequisition().getRequisitionNumber())
                .vendorId(po.getVendor().getId())
                .vendorName(po.getVendor().getVendorName())
                .createdByName(po.getCreatedBy().getFirstName() + " " + po.getCreatedBy().getLastName())
                .orderDate(po.getOrderDate())
                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                .totalAmount(po.getTotalAmount())
                .subtotal(po.getSubtotal())
                .taxAmount(po.getTaxAmount())
                .shippingCost(po.getShippingCost())
                .paymentTerms(po.getPaymentTerms())
                .shippingMethod(po.getShippingMethod())
                .notes(po.getNotes())
                .status(po.getStatus().name())
                .items(itemResponses)
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private PurchaseOrderItemResponse toItemResponse(PurchaseOrderItem item) {
        return PurchaseOrderItemResponse.builder()
                .id(item.getId())
                .itemName(item.getItemName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .build();
    }
}
