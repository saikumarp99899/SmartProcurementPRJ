package com.smartprocure.controller;

import com.smartprocure.dto.request.ChangeOrderRequest;
import com.smartprocure.dto.request.CreatePurchaseOrderRequest;
import com.smartprocure.dto.response.PageResponse;
import com.smartprocure.dto.response.PurchaseOrderResponse;
import com.smartprocure.entity.PurchaseOrder.PurchaseOrderStatus;
import com.smartprocure.security.user.UserPrincipal;
import com.smartprocure.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
@Tag(name = "Purchase Orders", description = "Create and manage purchase orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    /**
     * POST /api/purchase-orders
     * Creates a PO from an approved requisition.
     * BUYER creates POs after their requisition is approved.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN')")
    @Operation(summary = "Create a purchase order from an approved requisition")
    public ResponseEntity<PurchaseOrderResponse> createPurchaseOrder(
            @Valid @RequestBody CreatePurchaseOrderRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        PurchaseOrderResponse response = purchaseOrderService.createPurchaseOrder(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/purchase-orders?status=SENT&page=0&size=10
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "Get all purchase orders (Admin/Approver)")
    public ResponseEntity<PageResponse<PurchaseOrderResponse>> getAllPurchaseOrders(
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(purchaseOrderService.getAllPurchaseOrders(status, pageable));
    }

    /**
     * GET /api/purchase-orders/my
     * Returns POs created by the logged-in buyer.
     */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN')")
    @Operation(summary = "Get my purchase orders")
    public ResponseEntity<PageResponse<PurchaseOrderResponse>> getMyPurchaseOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(purchaseOrderService.getMyPurchaseOrders(currentUser.getId(), pageable));
    }

    /**
     * GET /api/purchase-orders/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER', 'APPROVER')")
    @Operation(summary = "Get purchase order by ID")
    public ResponseEntity<PurchaseOrderResponse> getPurchaseOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getPurchaseOrderById(id));
    }

    /**
     * PATCH /api/purchase-orders/{id}/status?newStatus=SENT
     * Updates the PO status through its lifecycle.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER')")
    @Operation(summary = "Update purchase order status")
    public ResponseEntity<PurchaseOrderResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam PurchaseOrderStatus newStatus) {

        return ResponseEntity.ok(purchaseOrderService.updatePurchaseOrderStatus(id, newStatus));
    }

    /**
     * PUT /api/purchase-orders/{id}/change-order
     * Applies a change order to an existing PO (only SENT or ACCEPTED).
     */
    @PutMapping("/{id}/change-order")
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN')")
    @Operation(summary = "Submit a change order for an existing purchase order")
    public ResponseEntity<PurchaseOrderResponse> changeOrder(
            @PathVariable Long id,
            @Valid @RequestBody ChangeOrderRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        PurchaseOrderResponse response = purchaseOrderService.changeOrder(id, request, currentUser.getId());
        return ResponseEntity.ok(response);
    }
}
