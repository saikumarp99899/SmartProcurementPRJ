package com.smartprocure.service;

import com.smartprocure.dto.request.ChangeOrderRequest;
import com.smartprocure.dto.request.CreatePurchaseOrderRequest;
import com.smartprocure.dto.response.PageResponse;
import com.smartprocure.dto.response.PurchaseOrderResponse;
import com.smartprocure.entity.PurchaseOrder.PurchaseOrderStatus;
import org.springframework.data.domain.Pageable;

public interface PurchaseOrderService {
    PurchaseOrderResponse createPurchaseOrder(CreatePurchaseOrderRequest request, Long createdByUserId);
    PurchaseOrderResponse getPurchaseOrderById(Long id);
    PurchaseOrderResponse updatePurchaseOrderStatus(Long id, PurchaseOrderStatus newStatus);
    PageResponse<PurchaseOrderResponse> getAllPurchaseOrders(PurchaseOrderStatus status, Pageable pageable);
    PageResponse<PurchaseOrderResponse> getMyPurchaseOrders(Long userId, Pageable pageable);
    PurchaseOrderResponse changeOrder(Long id, ChangeOrderRequest request, Long userId);
}
