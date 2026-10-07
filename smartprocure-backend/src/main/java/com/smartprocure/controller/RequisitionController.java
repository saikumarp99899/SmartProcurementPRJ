package com.smartprocure.controller;

import com.smartprocure.dto.request.CreateRequisitionRequest;
import com.smartprocure.dto.response.ApprovalResponse;
import com.smartprocure.dto.response.PageResponse;
import com.smartprocure.dto.response.RequisitionResponse;
import com.smartprocure.entity.Requisition.RequisitionStatus;
import com.smartprocure.security.user.UserPrincipal;
import com.smartprocure.service.ApprovalService;
import com.smartprocure.service.RequisitionService;
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

import java.util.List;

@RestController
@RequestMapping("/api/requisitions")
@RequiredArgsConstructor
@Tag(name = "Requisition Management", description = "Create and manage requisitions")
public class RequisitionController {

    private final RequisitionService requisitionService;
    private final ApprovalService approvalService;

    /**
     * POST /api/requisitions 
     * Creates a new DRAFT requisition for the logged-in buyer.
     * @AuthenticationPrincipal gives us the current user's details from the JWT.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN')")
    @Operation(summary = "Create a new requisition")
    public ResponseEntity<RequisitionResponse> createRequisition(
            @Valid @RequestBody CreateRequisitionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        RequisitionResponse response = requisitionService.createRequisition(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/requisitions/my?page=0&size=10
     * Returns only the logged-in user's requisitions.
     */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN')")
    @Operation(summary = "Get my requisitions")
    public ResponseEntity<PageResponse<RequisitionResponse>> getMyRequisitions(
            @RequestParam(required = false) RequisitionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(requisitionService.getMyRequisitions(currentUser.getId(), status, pageable));
    }

    /**
     * GET /api/requisitions?status=SUBMITTED&page=0&size=10
     * Returns all requisitions — admin and approver view.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "Get all requisitions (Admin/Approver)")
    public ResponseEntity<PageResponse<RequisitionResponse>> getAllRequisitions(
            @RequestParam(required = false) RequisitionStatus status,
            @RequestParam(required = false) Long requesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(requisitionService.getAllRequisitions(status, requesterId, pageable));
    }

    /**
     * GET /api/requisitions/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER', 'APPROVER')")
    @Operation(summary = "Get requisition by ID")
    public ResponseEntity<RequisitionResponse> getRequisitionById(@PathVariable Long id) {
        return ResponseEntity.ok(requisitionService.getRequisitionById(id));
    }

    /**
     * POST /api/requisitions/{id}/submit
     * Submits a DRAFT requisition for approval.
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN')")
    @Operation(summary = "Submit requisition for approval")
    public ResponseEntity<RequisitionResponse> submitRequisition(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        return ResponseEntity.ok(requisitionService.submitRequisition(id, currentUser.getId()));
    }

    /**
     * GET /api/requisitions/{id}/approvals
     * Gets the approval history for a specific requisition.
     */
    @GetMapping("/{id}/approvals")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER', 'BUYER')")
    @Operation(summary = "Get approval history for a requisition")
    public ResponseEntity<List<ApprovalResponse>> getApprovalsByRequisition(@PathVariable Long id) {
        return ResponseEntity.ok(approvalService.getApprovalsByRequisition(id));
    }
}
