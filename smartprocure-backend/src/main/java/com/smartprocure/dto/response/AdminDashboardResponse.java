package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AdminDashboardResponse {
    private long totalUsers;
    private long activeVendors;
    private long totalRequisitions;
    private long pendingApprovals;
    private long totalPurchaseOrders;
    private BigDecimal totalProcurementSpend;
}
