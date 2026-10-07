package com.smartprocure.service;

import com.smartprocure.dto.request.ApprovalWorkflowRequest;
import com.smartprocure.dto.response.ApprovalWorkflowResponse;

import java.math.BigDecimal;
import java.util.List;

public interface ApprovalWorkflowService {

    ApprovalWorkflowResponse createWorkflow(ApprovalWorkflowRequest request);

    ApprovalWorkflowResponse updateWorkflow(Long id, ApprovalWorkflowRequest request);

    ApprovalWorkflowResponse setActive(Long id, boolean active);

    void deleteWorkflow(Long id);

    List<ApprovalWorkflowResponse> getAllWorkflows();

    ApprovalWorkflowResponse getWorkflowById(Long id);

    /**
     * Shows which workflow would route the given amount and department, and
     * how its steps resolve, without creating anything.
     */
    ApprovalWorkflowResponse previewWorkflow(BigDecimal amount, Long departmentId);
}
