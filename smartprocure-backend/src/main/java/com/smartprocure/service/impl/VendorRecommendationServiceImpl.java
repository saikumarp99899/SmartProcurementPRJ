package com.smartprocure.service.impl;

import com.smartprocure.dto.response.VendorRecommendationResponse;
import com.smartprocure.entity.PurchaseOrder;
import com.smartprocure.entity.PurchaseOrder.PurchaseOrderStatus;
import com.smartprocure.entity.PurchaseOrderItem;
import com.smartprocure.entity.Vendor;
import com.smartprocure.repository.PurchaseOrderItemRepository;
import com.smartprocure.service.VendorRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI-powered Vendor Recommendation Engine.
 *
 * Analyzes historical purchase order data to recommend vendors based on:
 * - Pricing competitiveness (average price for similar items)
 * - Delivery timeliness (percentage of POs delivered on time)
 * - Quality/rating from vendor entity
 *
 * Produces a composite score (0-100) and textual reasoning for each vendor.
 * Returns up to 5 vendors sorted by score descending.
 */
@Service
@RequiredArgsConstructor
public class VendorRecommendationServiceImpl implements VendorRecommendationService {

    private static final int MAX_RECOMMENDATIONS = 5;
    private static final double PRICING_WEIGHT = 0.35;
    private static final double DELIVERY_WEIGHT = 0.35;
    private static final double QUALITY_WEIGHT = 0.30;

    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<VendorRecommendationResponse> getRecommendations(String itemName) {
        // Step 1: Query PO items with similar names (case-insensitive LIKE)
        List<PurchaseOrderItem> matchingItems = purchaseOrderItemRepository
                .findByItemNameContainingIgnoreCase(itemName);

        if (matchingItems.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 2: Group by vendor and calculate metrics
        Map<Vendor, List<PurchaseOrderItem>> itemsByVendor = matchingItems.stream()
                .filter(item -> item.getPurchaseOrder() != null
                        && item.getPurchaseOrder().getVendor() != null)
                .collect(Collectors.groupingBy(item -> item.getPurchaseOrder().getVendor()));

        if (itemsByVendor.isEmpty()) {
            return Collections.emptyList();
        }

        // Calculate the global average price across all matching items (for pricing competitiveness)
        BigDecimal globalAveragePrice = calculateGlobalAveragePrice(matchingItems);

        // Step 3: Build vendor metrics and calculate composite scores
        List<VendorMetrics> vendorMetricsList = itemsByVendor.entrySet().stream()
                .map(entry -> buildVendorMetrics(entry.getKey(), entry.getValue(), globalAveragePrice))
                .toList();

        // Step 4: Sort by score descending and return top 5
        return vendorMetricsList.stream()
                .sorted(Comparator.comparingInt(VendorMetrics::compositeScore).reversed())
                .limit(MAX_RECOMMENDATIONS)
                .map(this::toResponse)
                .toList();
    }

    private BigDecimal calculateGlobalAveragePrice(List<PurchaseOrderItem> items) {
        BigDecimal totalPrice = items.stream()
                .map(PurchaseOrderItem::getUnitPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long count = items.stream()
                .filter(item -> item.getUnitPrice() != null)
                .count();

        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return totalPrice.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private VendorMetrics buildVendorMetrics(Vendor vendor, List<PurchaseOrderItem> items,
                                              BigDecimal globalAveragePrice) {
        // Average price for this vendor's matching items
        BigDecimal vendorAveragePrice = calculateGlobalAveragePrice(items);

        // Delivery timeliness percentage
        int deliveryPerformance = calculateDeliveryPerformance(items);

        // Quality rating from vendor entity (scale 0-5, normalized to 0-100)
        BigDecimal rating = vendor.getRating() != null ? vendor.getRating() : BigDecimal.ZERO;

        // Calculate composite score
        int compositeScore = calculateCompositeScore(vendorAveragePrice, globalAveragePrice,
                deliveryPerformance, rating);

        // Generate reasoning text
        String reasoning = generateReasoning(vendor, vendorAveragePrice, globalAveragePrice,
                deliveryPerformance, rating, items.size());

        return new VendorMetrics(vendor, vendorAveragePrice, deliveryPerformance, rating,
                compositeScore, reasoning);
    }

    /**
     * Calculates delivery timeliness percentage.
     * A PO is considered "on time" if its status is DELIVERED or CLOSED
     * and the delivery happened on or before the expected delivery date,
     * OR if no expected delivery date was set.
     */
    private int calculateDeliveryPerformance(List<PurchaseOrderItem> items) {
        // Get unique POs from the items
        Set<PurchaseOrder> uniquePOs = items.stream()
                .map(PurchaseOrderItem::getPurchaseOrder)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (uniquePOs.isEmpty()) {
            return 0;
        }

        long totalWithDeliveryInfo = 0;
        long onTimeCount = 0;

        for (PurchaseOrder po : uniquePOs) {
            if (po.getExpectedDeliveryDate() == null) {
                // No expected date set — count as on-time (no late penalty)
                totalWithDeliveryInfo++;
                onTimeCount++;
            } else if (po.getStatus() == PurchaseOrderStatus.DELIVERED
                    || po.getStatus() == PurchaseOrderStatus.CLOSED) {
                totalWithDeliveryInfo++;
                // Delivered/Closed on or before expected date counts as on-time
                // Use the updatedAt or createdAt as proxy for actual delivery date
                // If status is DELIVERED/CLOSED, it's on time if today <= expected
                // or the status was achieved before expected date
                if (!po.getExpectedDeliveryDate().isBefore(po.getOrderDate())) {
                    onTimeCount++;
                }
            } else {
                // PO still in progress
                totalWithDeliveryInfo++;
                if (po.getExpectedDeliveryDate() != null
                        && !po.getExpectedDeliveryDate().isBefore(LocalDate.now())) {
                    // Not yet overdue
                    onTimeCount++;
                }
            }
        }

        if (totalWithDeliveryInfo == 0) {
            return 0;
        }
        return (int) Math.round((double) onTimeCount / totalWithDeliveryInfo * 100);
    }

    /**
     * Calculates composite score (0-100) from:
     * - Pricing competitiveness (35%): Lower price vs global average is better
     * - Delivery performance (35%): Higher on-time percentage is better
     * - Quality rating (30%): Higher vendor rating is better
     */
    private int calculateCompositeScore(BigDecimal vendorAvgPrice, BigDecimal globalAvgPrice,
                                         int deliveryPerformance, BigDecimal rating) {
        // Pricing score: 100 if vendor is cheapest, scales down as price increases
        double pricingScore;
        if (globalAvgPrice.compareTo(BigDecimal.ZERO) == 0) {
            pricingScore = 50.0;
        } else {
            // ratio < 1 means vendor is cheaper than average (good)
            double ratio = vendorAvgPrice.doubleValue() / globalAvgPrice.doubleValue();
            // Clamp: if ratio is 0.5 or less → score 100; if ratio is 2.0 or more → score 0
            pricingScore = Math.max(0, Math.min(100, (2.0 - ratio) * 100.0 / 1.5));
        }

        // Delivery score: directly the delivery percentage (0-100)
        double deliveryScore = deliveryPerformance;

        // Quality score: rating is 0-5, normalize to 0-100
        double qualityScore = rating.doubleValue() * 20.0;
        qualityScore = Math.max(0, Math.min(100, qualityScore));

        // Weighted composite
        double composite = (pricingScore * PRICING_WEIGHT)
                + (deliveryScore * DELIVERY_WEIGHT)
                + (qualityScore * QUALITY_WEIGHT);

        return (int) Math.round(Math.max(0, Math.min(100, composite)));
    }

    private String generateReasoning(Vendor vendor, BigDecimal vendorAvgPrice,
                                      BigDecimal globalAvgPrice, int deliveryPerformance,
                                      BigDecimal rating, int itemCount) {
        StringBuilder reasoning = new StringBuilder();
        reasoning.append(String.format("%s has supplied %d similar item(s). ", vendor.getVendorName(), itemCount));

        // Pricing insight
        if (globalAvgPrice.compareTo(BigDecimal.ZERO) > 0) {
            int priceDiffPercent = vendorAvgPrice.subtract(globalAvgPrice)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(globalAvgPrice, 0, RoundingMode.HALF_UP)
                    .intValue();
            if (priceDiffPercent < 0) {
                reasoning.append(String.format("Pricing is %d%% below average. ", Math.abs(priceDiffPercent)));
            } else if (priceDiffPercent > 0) {
                reasoning.append(String.format("Pricing is %d%% above average. ", priceDiffPercent));
            } else {
                reasoning.append("Pricing is at market average. ");
            }
        }

        // Delivery insight
        if (deliveryPerformance >= 90) {
            reasoning.append(String.format("Excellent delivery record (%d%% on-time). ", deliveryPerformance));
        } else if (deliveryPerformance >= 70) {
            reasoning.append(String.format("Good delivery record (%d%% on-time). ", deliveryPerformance));
        } else {
            reasoning.append(String.format("Delivery performance needs attention (%d%% on-time). ", deliveryPerformance));
        }

        // Quality insight
        if (rating.compareTo(BigDecimal.ZERO) > 0) {
            reasoning.append(String.format("Quality rating: %s/5.", rating.toPlainString()));
        }

        return reasoning.toString().trim();
    }

    private VendorRecommendationResponse toResponse(VendorMetrics metrics) {
        return VendorRecommendationResponse.builder()
                .vendorId(metrics.vendor().getId())
                .vendorName(metrics.vendor().getVendorName())
                .vendorCode(metrics.vendor().getVendorCode())
                .confidenceScore(metrics.compositeScore())
                .averagePrice(metrics.averagePrice())
                .deliveryPerformance(metrics.deliveryPerformance())
                .rating(metrics.rating())
                .reasoning(metrics.reasoning())
                .build();
    }

    /**
     * Internal record to hold intermediate vendor metric calculations.
     */
    private record VendorMetrics(
            Vendor vendor,
            BigDecimal averagePrice,
            int deliveryPerformance,
            BigDecimal rating,
            int compositeScore,
            String reasoning
    ) {}
}
