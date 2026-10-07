package com.smartprocure.service.impl;

import com.smartprocure.dto.response.AffectedEntity;
import com.smartprocure.dto.response.AnomalyResponse;
import com.smartprocure.entity.PurchaseOrder;
import com.smartprocure.entity.PurchaseOrder.PurchaseOrderStatus;
import com.smartprocure.entity.PurchaseOrderItem;
import com.smartprocure.entity.Requisition;
import com.smartprocure.repository.PurchaseOrderItemRepository;
import com.smartprocure.repository.PurchaseOrderRepository;
import com.smartprocure.repository.RequisitionRepository;
import com.smartprocure.service.AnomalyDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI-powered Anomaly Detection Service.
 *
 * Analyzes procurement data for three types of anomalies:
 * 1. Price anomalies — items priced > 2 standard deviations above historical mean
 * 2. Split ordering — requesters creating multiple below-threshold requisitions
 * 3. Vendor concentration — single vendor capturing > 60% of category spend
 *
 * Uses rule-based statistical analysis (no external ML/LLM required).
 */
@Service
@RequiredArgsConstructor
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    private static final int MIN_DATA_POINTS = 5;
    private static final double PRICE_ANOMALY_SIGMA_THRESHOLD = 2.0;
    private static final int SPLIT_ORDER_MIN_REQUISITIONS = 3;
    private static final int SPLIT_ORDER_WINDOW_DAYS = 7;
    private static final BigDecimal SPLIT_ORDER_APPROVAL_THRESHOLD = new BigDecimal("5000");
    private static final double VENDOR_CONCENTRATION_THRESHOLD = 0.60;
    private static final int VENDOR_CONCENTRATION_WINDOW_DAYS = 90;

    private static final BigDecimal HIGH_IMPACT_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal MEDIUM_IMPACT_THRESHOLD = new BigDecimal("2000");
    private static final double HIGH_SIGMA_THRESHOLD = 4.0;
    private static final double MEDIUM_SIGMA_THRESHOLD = 2.0;

    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final RequisitionRepository requisitionRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AnomalyResponse> detectAnomalies() {
        List<AnomalyResponse> anomalies = new ArrayList<>();
        long idCounter = 1L;

        // 1. Detect price anomalies
        List<AnomalyResponse> priceAnomalies = detectPriceAnomalies(idCounter);
        anomalies.addAll(priceAnomalies);
        idCounter += priceAnomalies.size();

        // 2. Detect split ordering patterns
        List<AnomalyResponse> splitOrderAnomalies = detectSplitOrdering(idCounter);
        anomalies.addAll(splitOrderAnomalies);
        idCounter += splitOrderAnomalies.size();

        // 3. Detect vendor concentration risks
        List<AnomalyResponse> vendorConcentrationAnomalies = detectVendorConcentration(idCounter);
        anomalies.addAll(vendorConcentrationAnomalies);

        return anomalies;
    }

    /**
     * Detects price anomalies by calculating mean and standard deviation per item category.
     * Flags items where unit price > mean + 2σ.
     * Skips categories with fewer than 5 data points.
     */
    List<AnomalyResponse> detectPriceAnomalies(long startId) {
        List<AnomalyResponse> anomalies = new ArrayList<>();
        List<PurchaseOrderItem> allItems = purchaseOrderItemRepository.findAll();

        // Group items by item name (category proxy)
        Map<String, List<PurchaseOrderItem>> itemsByCategory = allItems.stream()
                .collect(Collectors.groupingBy(item -> item.getItemName().toLowerCase().trim()));

        long idCounter = startId;

        for (Map.Entry<String, List<PurchaseOrderItem>> entry : itemsByCategory.entrySet()) {
            String category = entry.getKey();
            List<PurchaseOrderItem> categoryItems = entry.getValue();

            // Minimum data points guard
            if (categoryItems.size() < MIN_DATA_POINTS) {
                continue;
            }

            // Calculate mean
            BigDecimal sum = categoryItems.stream()
                    .map(PurchaseOrderItem::getUnitPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal mean = sum.divide(BigDecimal.valueOf(categoryItems.size()), 4, RoundingMode.HALF_UP);

            // Calculate standard deviation
            double stdDev = calculateStdDev(categoryItems, mean);

            if (stdDev == 0) {
                continue; // All prices identical, no anomaly possible
            }

            // Flag items exceeding mean + 2σ
            BigDecimal threshold = mean.add(BigDecimal.valueOf(PRICE_ANOMALY_SIGMA_THRESHOLD * stdDev));

            for (PurchaseOrderItem item : categoryItems) {
                if (item.getUnitPrice().compareTo(threshold) > 0) {
                    double sigmaDeviation = (item.getUnitPrice().doubleValue() - mean.doubleValue()) / stdDev;
                    BigDecimal impact = item.getUnitPrice().subtract(mean).multiply(BigDecimal.valueOf(item.getQuantity()));

                    String severity = classifySeverity(impact, sigmaDeviation);

                    anomalies.add(AnomalyResponse.builder()
                            .id(idCounter++)
                            .type("PRICE")
                            .severity(severity)
                            .description(String.format(
                                    "Price anomaly detected for '%s': unit price $%s exceeds historical mean $%s by %.1fσ (threshold: $%s)",
                                    item.getItemName(),
                                    item.getUnitPrice().setScale(2, RoundingMode.HALF_UP),
                                    mean.setScale(2, RoundingMode.HALF_UP),
                                    sigmaDeviation,
                                    threshold.setScale(2, RoundingMode.HALF_UP)))
                            .affectedEntities(List.of(
                                    AffectedEntity.builder()
                                            .entityType("PURCHASE_ORDER_ITEM")
                                            .entityId(item.getId())
                                            .entityName(item.getItemName())
                                            .build()))
                            .recommendedAction("Review pricing with vendor or seek alternative quotes for this item category.")
                            .detectedAt(LocalDateTime.now())
                            .build());
                }
            }
        }

        return anomalies;
    }

    /**
     * Detects split ordering patterns.
     * Identifies requesters with 3+ requisitions within a 7-day window where each individual
     * total is below the approval threshold and the combined total exceeds it.
     */
    List<AnomalyResponse> detectSplitOrdering(long startId) {
        List<AnomalyResponse> anomalies = new ArrayList<>();
        List<Requisition> allRequisitions = requisitionRepository.findAll();

        // Group requisitions by requester
        Map<Long, List<Requisition>> byRequester = allRequisitions.stream()
                .filter(r -> r.getRequester() != null && r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> r.getRequester().getId()));

        long idCounter = startId;

        for (Map.Entry<Long, List<Requisition>> entry : byRequester.entrySet()) {
            List<Requisition> requesterReqs = entry.getValue();

            // Sort by creation date
            requesterReqs.sort(Comparator.comparing(Requisition::getCreatedAt));

            // Sliding window approach: find clusters of 3+ requisitions within 7 days
            List<List<Requisition>> splitClusters = findSplitOrderClusters(requesterReqs);

            for (List<Requisition> cluster : splitClusters) {
                BigDecimal combinedTotal = cluster.stream()
                        .map(Requisition::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal impact = combinedTotal.subtract(SPLIT_ORDER_APPROVAL_THRESHOLD);
                String severity = classifySeverity(impact, 0);

                String requesterName = cluster.get(0).getRequester().getFirstName() + " " +
                        cluster.get(0).getRequester().getLastName();

                List<AffectedEntity> affectedEntities = cluster.stream()
                        .map(r -> AffectedEntity.builder()
                                .entityType("REQUISITION")
                                .entityId(r.getId())
                                .entityName(r.getRequisitionNumber())
                                .build())
                        .collect(Collectors.toList());

                anomalies.add(AnomalyResponse.builder()
                        .id(idCounter++)
                        .type("SPLIT_ORDER")
                        .severity(severity)
                        .description(String.format(
                                "Potential split ordering by %s: %d requisitions within 7 days totaling $%s " +
                                        "(each below $%s threshold, combined exceeds it)",
                                requesterName,
                                cluster.size(),
                                combinedTotal.setScale(2, RoundingMode.HALF_UP),
                                SPLIT_ORDER_APPROVAL_THRESHOLD.setScale(2, RoundingMode.HALF_UP)))
                        .affectedEntities(affectedEntities)
                        .recommendedAction("Review requisition pattern for potential threshold avoidance. Consider consolidating into a single requisition.")
                        .detectedAt(LocalDateTime.now())
                        .build());
            }
        }

        return anomalies;
    }

    /**
     * Detects vendor concentration anomalies.
     * For each item category over the last 90 days, flags if a single vendor has > 60% of spend.
     */
    List<AnomalyResponse> detectVendorConcentration(long startId) {
        List<AnomalyResponse> anomalies = new ArrayList<>();
        LocalDate cutoffDate = LocalDate.now().minusDays(VENDOR_CONCENTRATION_WINDOW_DAYS);

        // Get all non-cancelled POs within the 90-day window
        List<PurchaseOrder> recentOrders = purchaseOrderRepository.findAll().stream()
                .filter(po -> po.getStatus() != PurchaseOrderStatus.CANCELLED)
                .filter(po -> po.getOrderDate() != null && !po.getOrderDate().isBefore(cutoffDate))
                .toList();

        // Build a map of category -> vendor -> total spend
        Map<String, Map<Long, BigDecimal>> categoryVendorSpend = new HashMap<>();
        Map<String, BigDecimal> categoryTotalSpend = new HashMap<>();
        Map<Long, String> vendorNames = new HashMap<>();

        for (PurchaseOrder po : recentOrders) {
            if (po.getVendor() == null || po.getItems() == null) {
                continue;
            }
            Long vendorId = po.getVendor().getId();
            vendorNames.putIfAbsent(vendorId, po.getVendor().getVendorName());

            for (PurchaseOrderItem item : po.getItems()) {
                String category = item.getItemName().toLowerCase().trim();
                BigDecimal itemSpend = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;

                categoryVendorSpend
                        .computeIfAbsent(category, k -> new HashMap<>())
                        .merge(vendorId, itemSpend, BigDecimal::add);

                categoryTotalSpend.merge(category, itemSpend, BigDecimal::add);
            }
        }

        long idCounter = startId;

        for (Map.Entry<String, Map<Long, BigDecimal>> categoryEntry : categoryVendorSpend.entrySet()) {
            String category = categoryEntry.getKey();
            Map<Long, BigDecimal> vendorSpend = categoryEntry.getValue();
            BigDecimal totalSpend = categoryTotalSpend.getOrDefault(category, BigDecimal.ZERO);

            if (totalSpend.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // Check data points: count total items in this category across all POs
            long dataPoints = recentOrders.stream()
                    .flatMap(po -> po.getItems().stream())
                    .filter(item -> item.getItemName().toLowerCase().trim().equals(category))
                    .count();

            if (dataPoints < MIN_DATA_POINTS) {
                continue;
            }

            for (Map.Entry<Long, BigDecimal> vendorEntry : vendorSpend.entrySet()) {
                Long vendorId = vendorEntry.getKey();
                BigDecimal vendorTotal = vendorEntry.getValue();

                double concentration = vendorTotal.divide(totalSpend, 4, RoundingMode.HALF_UP).doubleValue();

                if (concentration > VENDOR_CONCENTRATION_THRESHOLD) {
                    BigDecimal impact = vendorTotal;
                    String severity = classifySeverity(impact, 0);
                    String vendorName = vendorNames.getOrDefault(vendorId, "Unknown Vendor");

                    anomalies.add(AnomalyResponse.builder()
                            .id(idCounter++)
                            .type("VENDOR_CONCENTRATION")
                            .severity(severity)
                            .description(String.format(
                                    "Vendor concentration risk in '%s': %s accounts for %.1f%% of spend ($%s of $%s) over the last 90 days",
                                    category,
                                    vendorName,
                                    concentration * 100,
                                    vendorTotal.setScale(2, RoundingMode.HALF_UP),
                                    totalSpend.setScale(2, RoundingMode.HALF_UP)))
                            .affectedEntities(List.of(
                                    AffectedEntity.builder()
                                            .entityType("VENDOR")
                                            .entityId(vendorId)
                                            .entityName(vendorName)
                                            .build()))
                            .recommendedAction("Diversify vendor base for this category. Consider soliciting quotes from alternative suppliers.")
                            .detectedAt(LocalDateTime.now())
                            .build());
                }
            }
        }

        return anomalies;
    }

    /**
     * Finds clusters of requisitions that match the split ordering pattern:
     * - 3+ requisitions within a 7-day window
     * - Each individual total is below the approval threshold
     * - Combined total exceeds the approval threshold
     */
    private List<List<Requisition>> findSplitOrderClusters(List<Requisition> sortedRequisitions) {
        List<List<Requisition>> clusters = new ArrayList<>();
        Set<Long> processedIds = new HashSet<>();

        for (int i = 0; i < sortedRequisitions.size(); i++) {
            Requisition anchor = sortedRequisitions.get(i);

            if (processedIds.contains(anchor.getId())) {
                continue;
            }

            // Skip requisitions that are already above the threshold individually
            if (anchor.getTotalAmount() == null ||
                    anchor.getTotalAmount().compareTo(SPLIT_ORDER_APPROVAL_THRESHOLD) >= 0) {
                continue;
            }

            // Find all requisitions within 7 days of the anchor that are below threshold
            LocalDateTime windowEnd = anchor.getCreatedAt().plusDays(SPLIT_ORDER_WINDOW_DAYS);
            List<Requisition> windowReqs = new ArrayList<>();
            windowReqs.add(anchor);

            for (int j = i + 1; j < sortedRequisitions.size(); j++) {
                Requisition candidate = sortedRequisitions.get(j);
                if (candidate.getCreatedAt().isAfter(windowEnd)) {
                    break;
                }
                if (candidate.getTotalAmount() != null &&
                        candidate.getTotalAmount().compareTo(SPLIT_ORDER_APPROVAL_THRESHOLD) < 0) {
                    windowReqs.add(candidate);
                }
            }

            // Check if cluster meets split ordering criteria
            if (windowReqs.size() >= SPLIT_ORDER_MIN_REQUISITIONS) {
                BigDecimal combinedTotal = windowReqs.stream()
                        .map(Requisition::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (combinedTotal.compareTo(SPLIT_ORDER_APPROVAL_THRESHOLD) > 0) {
                    clusters.add(new ArrayList<>(windowReqs));
                    windowReqs.forEach(r -> processedIds.add(r.getId()));
                }
            }
        }

        return clusters;
    }

    /**
     * Calculates standard deviation of unit prices for a category.
     */
    private double calculateStdDev(List<PurchaseOrderItem> items, BigDecimal mean) {
        double meanDouble = mean.doubleValue();
        double sumSquaredDiffs = items.stream()
                .mapToDouble(item -> {
                    double diff = item.getUnitPrice().doubleValue() - meanDouble;
                    return diff * diff;
                })
                .sum();

        return Math.sqrt(sumSquaredDiffs / items.size());
    }

    /**
     * Classifies anomaly severity based on financial impact and statistical deviation.
     * HIGH: impact > $10,000 or deviation > 4σ
     * MEDIUM: impact $2,000-$10,000 or deviation 2-4σ
     * LOW: all others
     */
    String classifySeverity(BigDecimal impact, double sigmaDeviation) {
        // HIGH if impact > 10,000 or deviation > 4σ
        if (impact.compareTo(HIGH_IMPACT_THRESHOLD) > 0 || sigmaDeviation > HIGH_SIGMA_THRESHOLD) {
            return "HIGH";
        }

        // MEDIUM if impact between 2,000 and 10,000 or deviation between 2 and 4σ
        if (impact.compareTo(MEDIUM_IMPACT_THRESHOLD) >= 0 || sigmaDeviation >= MEDIUM_SIGMA_THRESHOLD) {
            return "MEDIUM";
        }

        // LOW for all others
        return "LOW";
    }
}
