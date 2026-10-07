package com.smartprocure.service.impl;

import com.smartprocure.audit.AuditLogService;
import com.smartprocure.dto.request.ChangeOrderRequest;
import com.smartprocure.dto.request.CreatePurchaseOrderRequest;
import com.smartprocure.dto.response.PageResponse;
import com.smartprocure.dto.response.PurchaseOrderResponse;
import com.smartprocure.entity.*;
import com.smartprocure.entity.AuditLog.AuditAction;
import com.smartprocure.entity.PurchaseOrder.PurchaseOrderStatus;
import com.smartprocure.entity.Requisition.RequisitionStatus;
import com.smartprocure.entity.Vendor.VendorStatus;
import com.smartprocure.exception.BusinessRuleException;
import com.smartprocure.exception.ResourceNotFoundException;
import com.smartprocure.mapper.PurchaseOrderMapper;
import com.smartprocure.repository.*;
import com.smartprocure.service.PurchaseOrderService;
import com.smartprocure.util.NumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business rules enforced:
 * 1. Requisition must be in APPROVED status.
 * 2. No existing active PO for the same requisition.
 * 3. Vendor must be ACTIVE.
 * 4. PO items are copied from requisition items — totals recalculated.
 * 5. Requisition status moves to PO_CREATED after PO is created.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final RequisitionRepository requisitionRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final CompanyAddressRepository companyAddressRepository;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final AuditLogService auditLogService;
    private final NumberGenerator numberGenerator;

    @Override
    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(CreatePurchaseOrderRequest request, Long createdByUserId) {
        log.info("Creating PO for requisition id: {}, vendor id: {}, by user id: {}", request.getRequisitionId(), request.getVendorId(), createdByUserId);

        // Load required entities
        Requisition requisition = requisitionRepository.findById(request.getRequisitionId())
                .orElseThrow(() -> {
                    log.error("Requisition not found: id={}", request.getRequisitionId());
                    return new ResourceNotFoundException("Requisition", "id", request.getRequisitionId());
                });

        Vendor vendor = vendorRepository.findById(request.getVendorId())
                .orElseThrow(() -> {
                    log.error("Vendor not found: id={}", request.getVendorId());
                    return new ResourceNotFoundException("Vendor", "id", request.getVendorId());
                });

        User createdBy = userRepository.findById(createdByUserId)
                .orElseThrow(() -> {
                    log.error("User not found: id={}", createdByUserId);
                    return new ResourceNotFoundException("User", "id", createdByUserId);
                });

        // Business Rule 1: Requisition must be APPROVED
        if (requisition.getStatus() != RequisitionStatus.APPROVED) {
            log.error("Cannot create PO: Requisition {} status is {} (expected APPROVED)", requisition.getRequisitionNumber(), requisition.getStatus());
            throw new BusinessRuleException(
                "Purchase Order can only be created from an APPROVED requisition. " +
                "Current status: " + requisition.getStatus());
        }

        // Business Rule 2: Prevent duplicate PO for same requisition
        if (purchaseOrderRepository.existsByRequisitionId(requisition.getId())) {
            log.error("Duplicate PO attempt for requisition: {}", requisition.getRequisitionNumber());
            throw new BusinessRuleException(
                "A Purchase Order already exists for requisition: " + requisition.getRequisitionNumber());
        }

        // Business Rule 3: Vendor must be ACTIVE
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            log.error("Cannot create PO with inactive vendor: {} [status={}]", vendor.getVendorCode(), vendor.getStatus());
            throw new BusinessRuleException(
                "Cannot create PO for an inactive/suspended vendor: " + vendor.getVendorCode());
        }

        // Build PO
        PurchaseOrder po = PurchaseOrder.builder()
                .poNumber(numberGenerator.generatePONumber())
                .requisition(requisition)
                .vendor(vendor)
                .createdBy(createdBy)
                .orderDate(LocalDate.now())
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .paymentTerms(request.getPaymentTerms() != null ? request.getPaymentTerms() : "30 Days Net")
                .shippingMethod(request.getShippingMethod())
                .notes(request.getNotes())
                .taxAmount(request.getTaxAmount() != null ? request.getTaxAmount() : java.math.BigDecimal.ZERO)
                .shippingCost(request.getShippingCost() != null ? request.getShippingCost() : java.math.BigDecimal.ZERO)
                .status(PurchaseOrderStatus.DRAFT)
                .build();

        // Set addresses if provided
        if (request.getShipToAddressId() != null) {
            companyAddressRepository.findById(request.getShipToAddressId())
                    .ifPresent(po::setShipToAddress);
        }
        if (request.getBillToAddressId() != null) {
            companyAddressRepository.findById(request.getBillToAddressId())
                    .ifPresent(po::setBillToAddress);
        }

        // Copy line items from requisition items (or use overrides if provided)
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (CreatePurchaseOrderRequest.LineItemOverride override : request.getItems()) {
                PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                        .itemName(override.getItemName())
                        .quantity(override.getQuantity())
                        .unitPrice(override.getUnitPrice())
                        .build();
                poItem.calculateTotalPrice();
                po.addItem(poItem);
            }
        } else {
            for (RequisitionItem reqItem : requisition.getItems()) {
                PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                        .itemName(reqItem.getItemName())
                        .quantity(reqItem.getQuantity())
                        .unitPrice(reqItem.getUnitPrice())
                        .build();
                poItem.calculateTotalPrice();
                po.addItem(poItem);
            }
        }

        po.recalculateTotalAmount();

        PurchaseOrder saved = purchaseOrderRepository.save(po);

        // Update requisition status to PO_CREATED
        requisition.setStatus(RequisitionStatus.PO_CREATED);
        requisitionRepository.save(requisition);

        log.info("Purchase Order created: {}", saved.getPoNumber());
        auditLogService.log(createdBy, AuditAction.PO_CREATED, "PURCHASE_ORDER", saved.getId(),
                "PO created: " + saved.getPoNumber() + " for " + vendor.getVendorName());

        return purchaseOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponse getPurchaseOrderById(Long id) {
        log.info("Fetching purchase order by id: {}", id);
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Purchase order not found: id={}", id);
                    return new ResourceNotFoundException("PurchaseOrder", "id", id);
                });
        log.debug("PO found: {} [status={}, vendor={}]", po.getPoNumber(), po.getStatus(), po.getVendor().getVendorName());
        return purchaseOrderMapper.toResponse(po);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse updatePurchaseOrderStatus(Long id, PurchaseOrderStatus newStatus) {
        log.info("Updating PO id: {} to status: {}", id, newStatus);
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Purchase order not found for status update: id={}", id);
                    return new ResourceNotFoundException("PurchaseOrder", "id", id);
                });

        validateStatusTransition(po.getStatus(), newStatus);
        log.info("PO {} status transition: {} → {}", po.getPoNumber(), po.getStatus(), newStatus);
        po.setStatus(newStatus);

        // If PO is delivered, mark the requisition as completed
        if (newStatus == PurchaseOrderStatus.DELIVERED) {
            Requisition req = po.getRequisition();
            req.setStatus(RequisitionStatus.COMPLETED);
            requisitionRepository.save(req);
        }

        PurchaseOrder updated = purchaseOrderRepository.save(po);
        auditLogService.log(po.getCreatedBy(), AuditAction.PO_UPDATED, "PURCHASE_ORDER", updated.getId(),
                "PO status updated to " + newStatus + ": " + updated.getPoNumber());

        return purchaseOrderMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> getAllPurchaseOrders(PurchaseOrderStatus status, Pageable pageable) {
        log.info("Fetching all purchase orders [status={}, page={}, size={}]", status, pageable.getPageNumber(), pageable.getPageSize());
        Page<PurchaseOrder> page;
        if (status != null) {
            page = purchaseOrderRepository.findByStatus(status, pageable);
        } else {
            page = purchaseOrderRepository.findAll(pageable);
        }
        log.info("Found {} total purchase orders", page.getTotalElements());
        return toPageResponse(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> getMyPurchaseOrders(Long userId, Pageable pageable) {
        log.info("Fetching purchase orders for user id: {} [page={}]", userId, pageable.getPageNumber());
        Page<PurchaseOrder> page = purchaseOrderRepository.findByCreatedById(userId, pageable);
        log.info("Found {} purchase orders for user id: {}", page.getTotalElements(), userId);
        return toPageResponse(page);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse changeOrder(Long id, ChangeOrderRequest request, Long userId) {
        log.info("Processing change order for PO id: {}, by user id: {}", id, userId);

        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Purchase order not found for change order: id={}", id);
                    return new ResourceNotFoundException("PurchaseOrder", "id", id);
                });

        // Validate status — change orders only allowed for SENT or ACCEPTED
        if (po.getStatus() != PurchaseOrderStatus.SENT && po.getStatus() != PurchaseOrderStatus.ACCEPTED) {
            throw new BusinessRuleException(
                "Change orders are only allowed for purchase orders in SENT or ACCEPTED status. Current status: " + po.getStatus());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Update delivery date if provided
        if (request.getExpectedDeliveryDate() != null) {
            po.setExpectedDeliveryDate(request.getExpectedDeliveryDate());
        }

        // Update payment terms if provided
        if (request.getPaymentTerms() != null) {
            po.setPaymentTerms(request.getPaymentTerms());
        }

        // Update shipping method if provided
        if (request.getShippingMethod() != null) {
            po.setShippingMethod(request.getShippingMethod());
        }

        // Replace line items with the new set
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            po.getItems().clear();
            for (ChangeOrderRequest.LineItemChange itemChange : request.getItems()) {
                PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                        .itemName(itemChange.getItemName())
                        .quantity(itemChange.getQuantity())
                        .unitPrice(itemChange.getUnitPrice())
                        .build();
                poItem.calculateTotalPrice();
                po.addItem(poItem);
            }
        }

        // Recalculate totals
        po.recalculateTotalAmount();

        PurchaseOrder updated = purchaseOrderRepository.save(po);
        log.info("Change order applied to PO: {} [reason: {}]", updated.getPoNumber(), request.getChangeReason());

        // Audit log
        auditLogService.log(user, AuditAction.PO_UPDATED, "PURCHASE_ORDER", updated.getId(),
                "Change order applied to " + updated.getPoNumber() + ". Reason: " + request.getChangeReason());

        return purchaseOrderMapper.toResponse(updated);
    }

    /**
     * Validates that the status transition is allowed.
     * Valid transitions: DRAFT→SENT, SENT→ACCEPTED, ACCEPTED→DELIVERED, DELIVERED→CLOSED
     * CANCELLED can happen from DRAFT, SENT, or ACCEPTED.
     */
    private void validateStatusTransition(PurchaseOrderStatus current, PurchaseOrderStatus next) {
        boolean valid = switch (current) {
            case DRAFT     -> next == PurchaseOrderStatus.SENT || next == PurchaseOrderStatus.CANCELLED;
            case SENT      -> next == PurchaseOrderStatus.ACCEPTED || next == PurchaseOrderStatus.CANCELLED;
            case ACCEPTED  -> next == PurchaseOrderStatus.DELIVERED || next == PurchaseOrderStatus.CANCELLED;
            case DELIVERED -> next == PurchaseOrderStatus.CLOSED;
            case CLOSED, CANCELLED -> false; // Terminal states
        };
        if (!valid) {
            throw new BusinessRuleException(
                "Invalid status transition from " + current + " to " + next);
        }
    }

    private PageResponse<PurchaseOrderResponse> toPageResponse(Page<PurchaseOrder> page) {
        List<PurchaseOrderResponse> data = page.getContent().stream()
                .map(purchaseOrderMapper::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<PurchaseOrderResponse>builder()
                .data(data)
                .currentPage(page.getNumber())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageSize(page.getSize())
                .last(page.isLast())
                .build();
    }
}
