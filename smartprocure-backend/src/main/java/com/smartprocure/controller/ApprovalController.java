package com.smartprocure.controller;

import com.smartprocure.dto.request.ApprovalActionRequest;
import com.smartprocure.dto.response.ApprovalResponse;
import com.smartprocure.dto.response.PageResponse;
import com.smartprocure.security.user.UserPrincipal;
import com.smartprocure.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
@Tag(name = "Approval Workflow", description = "Approve or reject requisitions")
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * GET /api/approvals/pending?page=0&size=10
     * Returns the PENDING approvals the caller can act on.
     * Approvers see only their assigned levels; admins see all.
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    @Operation(summary = "Get pending approvals")
    public ResponseEntity<PageResponse<ApprovalResponse>> getPendingApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(approvalService.getPendingApprovals(currentUser.getId(), pageable));
    }

    /**
     * GET /api/approvals/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    @Operation(summary = "Get approval by ID")
    public ResponseEntity<ApprovalResponse> getApprovalById(@PathVariable Long id) {
        return ResponseEntity.ok(approvalService.getApprovalById(id));
    }

    /**
     * POST /api/approvals/{id}/approve
     * Approves a pending requisition.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    @Operation(summary = "Approve a requisition")
    public ResponseEntity<ApprovalResponse> approveRequisition(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalActionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        return ResponseEntity.ok(approvalService.approveRequisition(id, request, currentUser.getId()));
    }

    /**
     * POST /api/approvals/{id}/reject
     * Rejects a pending requisition. Comments are required.
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    @Operation(summary = "Reject a requisition")
    public ResponseEntity<ApprovalResponse> rejectRequisition(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalActionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        return ResponseEntity.ok(approvalService.rejectRequisition(id, request, currentUser.getId()));
    }
}
