package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BuyerDashboardResponse {
    private long totalMyRequisitions;
    private long draftRequisitions;
    private long pendingRequisitions;
    private long approvedRequisitions;
    private long rejectedRequisitions;
    private long myPurchaseOrders;
    private BigDecimal myTotalSpend;
}
