package com.smartprocure.service.impl;

import com.smartprocure.audit.AuditLogService;
import com.smartprocure.dto.request.CreateRequisitionRequest;
import com.smartprocure.dto.request.RequisitionItemRequest;
import com.smartprocure.dto.response.PageResponse;
import com.smartprocure.dto.response.RequisitionResponse;
import com.smartprocure.entity.*;
import com.smartprocure.entity.AuditLog.AuditAction;
import com.smartprocure.entity.Requisition.RequisitionStatus;
import com.smartprocure.exception.BusinessRuleException;
import com.smartprocure.exception.ForbiddenException;
import com.smartprocure.exception.ResourceNotFoundException;
import com.smartprocure.mapper.RequisitionMapper;
import com.smartprocure.repository.*;
import com.smartprocure.service.ApprovalChainService;
import com.smartprocure.service.RequisitionService;
import com.smartprocure.util.NumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles requisition lifecycle.
 *
 * Key business rules enforced here (not in controller):
 * 1. Only the original requester can submit their own DRAFT requisition.
 * 2. Only DRAFT requisitions can be submitted.
 * 3. Total amount is always calculated from items — never from frontend input.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequisitionServiceImpl implements RequisitionService {

    private final RequisitionRepository requisitionRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final CostCenterRepository costCenterRepository;
    private final ApprovalChainService approvalChainService;
    private final RequisitionMapper requisitionMapper;
    private final AuditLogService auditLogService;
    private final NumberGenerator numberGenerator;

    @Override
    @Transactional
    public RequisitionResponse createRequisition(CreateRequisitionRequest request, Long requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", requesterId));

        // Build the requisition shell
        Requisition requisition = Requisition.builder()
                .requisitionNumber(numberGenerator.generateRequisitionNumber())
                .requester(requester)
                .description(request.getDescription())
                .status(RequisitionStatus.DRAFT)
                .build();

        // Optionally set department and cost center
        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.getDepartmentId()));
            requisition.setDepartment(dept);
        }
        if (request.getCostCenterId() != null) {
            CostCenter cc = costCenterRepository.findById(request.getCostCenterId())
                    .orElseThrow(() -> new ResourceNotFoundException("CostCenter", "id", request.getCostCenterId()));
            requisition.setCostCenter(cc);
        }

        // Add line items — calculate each item's total price on the backend
        for (RequisitionItemRequest itemReq : request.getItems()) {
            RequisitionItem item = RequisitionItem.builder()
                    .itemName(itemReq.getItemName())
                    .description(itemReq.getDescription())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .build();
            item.calculateTotalPrice(); // quantity × unitPrice
            requisition.addItem(item);
        }

        // Calculate overall total from all items
        requisition.recalculateTotalAmount();

        Requisition saved = requisitionRepository.save(requisition);
        log.info("Requisition created: {}", saved.getRequisitionNumber());

        auditLogService.log(requester, AuditAction.REQUISITION_CREATED,
                "REQUISITION", saved.getId(),
                "Requisition created: " + saved.getRequisitionNumber());

        return requisitionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public RequisitionResponse getRequisitionById(Long id) {
        log.info("Fetching requisition by id: {}", id);
        Requisition req = requisitionRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Requisition not found with id: {}", id);
                    return new ResourceNotFoundException("Requisition", "id", id);
                });
        log.debug("Requisition found: {} [status={}]", req.getRequisitionNumber(), req.getStatus());
        return requisitionMapper.toResponse(req);
    }

    @Override
    @Transactional
    public RequisitionResponse submitRequisition(Long id, Long requesterId) {
        log.info("Submitting requisition id: {} by user id: {}", id, requesterId);
        Requisition req = requisitionRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Requisition not found for submission: id={}", id);
                    return new ResourceNotFoundException("Requisition", "id", id);
                });

        // Business Rule 1: Only the original requester can submit
        if (!req.getRequester().getId().equals(requesterId)) {
            log.error("Forbidden: User {} tried to submit requisition {} owned by user {}", requesterId, id, req.getRequester().getId());
            throw new ForbiddenException("You can only submit your own requisitions.");
        }

        // Business Rule 2: Only DRAFT can be submitted
        if (req.getStatus() != RequisitionStatus.DRAFT) {
            log.error("Invalid status transition: Cannot submit requisition {} with status {}", req.getRequisitionNumber(), req.getStatus());
            throw new BusinessRuleException(
                "Only DRAFT requisitions can be submitted. Current status: " + req.getStatus());
        }

        req.setStatus(RequisitionStatus.SUBMITTED);
        Requisition updated = requisitionRepository.save(req);

        // Routes the requisition through the configured approval workflow.
        approvalChainService.buildChain(updated);

        User requester = req.getRequester();
        auditLogService.log(requester, AuditAction.REQUISITION_SUBMITTED,
                "REQUISITION", updated.getId(),
                "Requisition submitted: " + updated.getRequisitionNumber());

        log.info("Requisition submitted: {}", updated.getRequisitionNumber());
        return requisitionMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RequisitionResponse> getMyRequisitions(Long requesterId, RequisitionStatus status, Pageable pageable) {
        log.info("Fetching requisitions for user id: {} [status={}, page={}, size={}]",
                requesterId, status, pageable.getPageNumber(), pageable.getPageSize());

        Page<Requisition> page = (status == null)
                ? requisitionRepository.findByRequesterId(requesterId, pageable)
                : requisitionRepository.findByRequesterIdAndStatus(requesterId, status, pageable);

        log.info("Found {} requisitions for user id: {}", page.getTotalElements(), requesterId);
        return toPageResponse(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RequisitionResponse> getAllRequisitions(RequisitionStatus status, Long requesterId, Pageable pageable) {
        log.info("Fetching all requisitions [status={}, requesterId={}, page={}]", status, requesterId, pageable.getPageNumber());
        Page<Requisition> page = requisitionRepository.findByStatusAndRequesterId(status, requesterId, pageable);
        log.info("Found {} total requisitions", page.getTotalElements());
        return toPageResponse(page);
    }

    private PageResponse<RequisitionResponse> toPageResponse(Page<Requisition> page) {
        List<RequisitionResponse> data = page.getContent().stream()
                .map(requisitionMapper::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<RequisitionResponse>builder()
                .data(data)
                .currentPage(page.getNumber())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageSize(page.getSize())
                .last(page.isLast())
                .build();
    }
}
