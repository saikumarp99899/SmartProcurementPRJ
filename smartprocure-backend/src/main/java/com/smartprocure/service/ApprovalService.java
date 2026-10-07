package com.smartprocure.service;

import com.smartprocure.dto.request.ApprovalActionRequest;
import com.smartprocure.dto.response.ApprovalResponse;
import com.smartprocure.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ApprovalService {
    ApprovalResponse approveRequisition(Long approvalId, ApprovalActionRequest request, Long approverId);
    ApprovalResponse rejectRequisition(Long approvalId, ApprovalActionRequest request, Long approverId);
    /**
     * Pending approvals the given user is allowed to action.
     * Admins receive all pending approvals.
     */
    PageResponse<ApprovalResponse> getPendingApprovals(Long currentUserId, Pageable pageable);
    List<ApprovalResponse> getApprovalsByRequisition(Long requisitionId);
    ApprovalResponse getApprovalById(Long id);
}
