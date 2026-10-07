package com.smartprocure.controller;

import com.smartprocure.dto.request.ApprovalWorkflowRequest;
import com.smartprocure.dto.response.ApprovalWorkflowResponse;
import com.smartprocure.service.ApprovalWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Admin configuration of approval workflows.
 *
 * Mutations are ADMIN-only, since changing who approves what is privileged.
 * The read-only preview is open to buyers so they can see the route their
 * requisition will take before submitting it.
 */
@RestController
@RequestMapping("/api/approval-workflows")
@RequiredArgsConstructor
@Tag(name = "Approval Workflows", description = "Configure approval routes and their steps")
public class ApprovalWorkflowController {

    private final ApprovalWorkflowService approvalWorkflowService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all approval workflows (Admin only)")
    public ResponseEntity<List<ApprovalWorkflowResponse>> getAllWorkflows() {
        return ResponseEntity.ok(approvalWorkflowService.getAllWorkflows());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get approval workflow by ID (Admin only)")
    public ResponseEntity<ApprovalWorkflowResponse> getWorkflowById(@PathVariable Long id) {
        return ResponseEntity.ok(approvalWorkflowService.getWorkflowById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create an approval workflow (Admin only)")
    public ResponseEntity<ApprovalWorkflowResponse> createWorkflow(
            @Valid @RequestBody ApprovalWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalWorkflowService.createWorkflow(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an approval workflow (Admin only)")
    public ResponseEntity<ApprovalWorkflowResponse> updateWorkflow(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalWorkflowRequest request) {
        return ResponseEntity.ok(approvalWorkflowService.updateWorkflow(id, request));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or deactivate a workflow (Admin only)")
    public ResponseEntity<ApprovalWorkflowResponse> setActive(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(approvalWorkflowService.setActive(id, active));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an approval workflow (Admin only)")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable Long id) {
        approvalWorkflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the workflow that would route the given amount, or 204 when
     * none matches (meaning the unrouted fallback would apply).
     */
    @GetMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER')")
    @Operation(summary = "Preview which workflow would route a given amount")
    public ResponseEntity<ApprovalWorkflowResponse> previewWorkflow(
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) Long departmentId) {

        ApprovalWorkflowResponse match = approvalWorkflowService.previewWorkflow(amount, departmentId);
        return match == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(match);
    }
}
