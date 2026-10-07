package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI-generated spend analysis response.
 * Provides insights into procurement spending patterns,
 * vendor distribution, and cost-saving recommendations.
 */
@Getter
@Builder
public class SpendAnalysisResponse {

    private BigDecimal totalSpend;
    private BigDecimal averageOrderValue;
    private long totalOrders;
    private long activeVendors;

    // Monthly spend trend
    private List<MonthlySpend> monthlyTrend;

    // Top vendors by spend
    private List<VendorSpend> topVendors;

    // AI-generated insights
    private List<String> insights;

    // Cost-saving recommendations
    private List<String> recommendations;

    @Getter
    @Builder
    public static class MonthlySpend {
        private String month;
        private BigDecimal amount;
        private long orderCount;
    }

    @Getter
    @Builder
    public static class VendorSpend {
        private String vendorName;
        private BigDecimal totalSpend;
        private long orderCount;
        private double percentageOfTotal;
    }
}
