package com.smartprocure.service.impl;

import com.smartprocure.dto.response.SpendAnalysisResponse;
import com.smartprocure.dto.response.SpendAnalysisResponse.MonthlySpend;
import com.smartprocure.dto.response.SpendAnalysisResponse.VendorSpend;
import com.smartprocure.entity.PurchaseOrder;
import com.smartprocure.entity.PurchaseOrder.PurchaseOrderStatus;
import com.smartprocure.entity.Vendor.VendorStatus;
import com.smartprocure.repository.PurchaseOrderRepository;
import com.smartprocure.repository.VendorRepository;
import com.smartprocure.service.SpendAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI-powered Spend Analysis Service.
 *
 * Analyzes procurement data to generate insights:
 * - Monthly spending trends
 * - Vendor concentration analysis
 * - Cost-saving recommendations
 * - Anomaly detection (unusual spending patterns)
 *
 * In a production system, this could integrate with OpenAI/LLM APIs
 * for more sophisticated natural language insights. Currently uses
 * rule-based analysis on procurement data.
 */
@Service
@RequiredArgsConstructor
public class SpendAnalysisServiceImpl implements SpendAnalysisService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final VendorRepository vendorRepository;

    @Override
    @Transactional(readOnly = true)
    public SpendAnalysisResponse getSpendAnalysis() {
        // Fetch all non-cancelled POs
        List<PurchaseOrder> allOrders = purchaseOrderRepository.findAll().stream()
                .filter(po -> po.getStatus() != PurchaseOrderStatus.CANCELLED)
                .toList();

        BigDecimal totalSpend = calculateTotalSpend(allOrders);
        long totalOrders = allOrders.size();
        BigDecimal averageOrderValue = totalOrders > 0
                ? totalSpend.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        long activeVendors = vendorRepository.countByStatus(VendorStatus.ACTIVE);

        List<MonthlySpend> monthlyTrend = calculateMonthlyTrend(allOrders);
        List<VendorSpend> topVendors = calculateTopVendors(allOrders, totalSpend);
        List<String> insights = generateInsights(allOrders, monthlyTrend, topVendors, totalSpend);
        List<String> recommendations = generateRecommendations(allOrders, topVendors, averageOrderValue);

        return SpendAnalysisResponse.builder()
                .totalSpend(totalSpend)
                .averageOrderValue(averageOrderValue)
                .totalOrders(totalOrders)
                .activeVendors(activeVendors)
                .monthlyTrend(monthlyTrend)
                .topVendors(topVendors)
                .insights(insights)
                .recommendations(recommendations)
                .build();
    }

    private BigDecimal calculateTotalSpend(List<PurchaseOrder> orders) {
        return orders.stream()
                .map(PurchaseOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<MonthlySpend> calculateMonthlyTrend(List<PurchaseOrder> orders) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy");

        Map<String, List<PurchaseOrder>> byMonth = orders.stream()
                .collect(Collectors.groupingBy(po ->
                        po.getOrderDate().withDayOfMonth(1).format(formatter),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return byMonth.entrySet().stream()
                .map(entry -> MonthlySpend.builder()
                        .month(entry.getKey())
                        .amount(entry.getValue().stream()
                                .map(PurchaseOrder::getTotalAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .orderCount(entry.getValue().size())
                        .build())
                .sorted((a, b) -> 0) // maintain insertion order
                .collect(Collectors.toList());
    }

    private List<VendorSpend> calculateTopVendors(List<PurchaseOrder> orders, BigDecimal totalSpend) {
        Map<String, List<PurchaseOrder>> byVendor = orders.stream()
                .filter(po -> po.getVendor() != null)
                .collect(Collectors.groupingBy(po -> po.getVendor().getVendorName()));

        return byVendor.entrySet().stream()
                .map(entry -> {
                    BigDecimal vendorTotal = entry.getValue().stream()
                            .map(PurchaseOrder::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    double percentage = totalSpend.compareTo(BigDecimal.ZERO) > 0
                            ? vendorTotal.divide(totalSpend, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100)).doubleValue()
                            : 0.0;
                    return VendorSpend.builder()
                            .vendorName(entry.getKey())
                            .totalSpend(vendorTotal)
                            .orderCount(entry.getValue().size())
                            .percentageOfTotal(percentage)
                            .build();
                })
                .sorted((a, b) -> b.getTotalSpend().compareTo(a.getTotalSpend()))
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * AI Insight Generation Engine.
     * Analyzes spending patterns and generates human-readable insights.
     */
    private List<String> generateInsights(List<PurchaseOrder> orders,
                                          List<MonthlySpend> monthlyTrend,
                                          List<VendorSpend> topVendors,
                                          BigDecimal totalSpend) {
        List<String> insights = new ArrayList<>();

        // Insight 1: Monthly trend analysis
        if (monthlyTrend.size() >= 2) {
            MonthlySpend latest = monthlyTrend.get(monthlyTrend.size() - 1);
            MonthlySpend previous = monthlyTrend.get(monthlyTrend.size() - 2);
            if (previous.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change = latest.getAmount().subtract(previous.getAmount())
                        .divide(previous.getAmount(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                if (change.compareTo(BigDecimal.ZERO) > 0) {
                    insights.add(String.format("Spending increased by %.1f%% in %s compared to %s.",
                            change.doubleValue(), latest.getMonth(), previous.getMonth()));
                } else {
                    insights.add(String.format("Spending decreased by %.1f%% in %s compared to %s.",
                            change.abs().doubleValue(), latest.getMonth(), previous.getMonth()));
                }
            }
        }

        // Insight 2: Vendor concentration risk
        if (!topVendors.isEmpty() && topVendors.get(0).getPercentageOfTotal() > 50) {
            insights.add(String.format("High vendor concentration risk: %s accounts for %.1f%% of total spend.",
                    topVendors.get(0).getVendorName(), topVendors.get(0).getPercentageOfTotal()));
        }

        // Insight 3: Order volume
        if (orders.size() > 0) {
            long thisMonthOrders = orders.stream()
                    .filter(po -> po.getOrderDate().getMonth() == LocalDate.now().getMonth()
                            && po.getOrderDate().getYear() == LocalDate.now().getYear())
                    .count();
            insights.add(String.format("%d purchase orders placed this month with total spend of ₹%s.",
                    thisMonthOrders, totalSpend.toPlainString()));
        }

        // Insight 4: Average order value context
        if (orders.size() > 0) {
            BigDecimal avg = totalSpend.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
            insights.add(String.format("Average order value is ₹%s across %d orders.",
                    avg.toPlainString(), orders.size()));
        }

        // Default insight if no data
        if (insights.isEmpty()) {
            insights.add("Insufficient data for trend analysis. Create more purchase orders to generate insights.");
        }

        return insights;
    }

    /**
     * AI Recommendation Engine.
     * Generates cost-saving and risk-reduction recommendations.
     */
    private List<String> generateRecommendations(List<PurchaseOrder> orders,
                                                  List<VendorSpend> topVendors,
                                                  BigDecimal averageOrderValue) {
        List<String> recommendations = new ArrayList<>();

        // Recommendation 1: Vendor diversification
        if (!topVendors.isEmpty() && topVendors.get(0).getPercentageOfTotal() > 40) {
            recommendations.add("Consider diversifying suppliers. Over-reliance on a single vendor increases supply chain risk. Aim for no single vendor exceeding 30% of total spend.");
        }

        // Recommendation 2: Bulk ordering for frequent small orders
        long smallOrders = orders.stream()
                .filter(po -> po.getTotalAmount().compareTo(averageOrderValue.multiply(BigDecimal.valueOf(0.3))) < 0)
                .count();
        if (smallOrders > 3) {
            recommendations.add(String.format("Consolidate %d small orders into bulk purchases to negotiate volume discounts and reduce processing costs.", smallOrders));
        }

        // Recommendation 3: Delivery performance
        long overdueOrders = orders.stream()
                .filter(po -> po.getExpectedDeliveryDate() != null
                        && po.getStatus() != PurchaseOrderStatus.DELIVERED
                        && po.getStatus() != PurchaseOrderStatus.CLOSED
                        && po.getExpectedDeliveryDate().isBefore(LocalDate.now()))
                .count();
        if (overdueOrders > 0) {
            recommendations.add(String.format("%d orders are past their expected delivery date. Review vendor performance and consider penalty clauses in future contracts.", overdueOrders));
        }

        // Recommendation 4: Draft orders cleanup
        long draftOrders = orders.stream()
                .filter(po -> po.getStatus() == PurchaseOrderStatus.DRAFT)
                .count();
        if (draftOrders > 2) {
            recommendations.add(String.format("%d purchase orders are still in DRAFT status. Review and either submit or cancel them to maintain clean records.", draftOrders));
        }

        // Default recommendation
        if (recommendations.isEmpty()) {
            recommendations.add("Procurement operations are running efficiently. Continue monitoring vendor performance and spending trends.");
        }

        return recommendations;
    }
}
