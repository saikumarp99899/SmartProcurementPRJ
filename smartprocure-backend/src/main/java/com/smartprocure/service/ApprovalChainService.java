package com.smartprocure.service;

import com.smartprocure.entity.ApprovalWorkflow;
import com.smartprocure.entity.Requisition;

import java.math.BigDecimal;
import java.util.Optional;

public interface ApprovalChainService {

    /**
     * Creates the approval chain for a freshly submitted requisition.
     * The first actionable step becomes PENDING; the rest start WAITING.
     */
    void buildChain(Requisition requisition);

    /**
     * The workflow that would route the given amount and department,
     * or empty when nothing matches.
     */
    Optional<ApprovalWorkflow> selectWorkflow(BigDecimal amount, Long departmentId);
}
