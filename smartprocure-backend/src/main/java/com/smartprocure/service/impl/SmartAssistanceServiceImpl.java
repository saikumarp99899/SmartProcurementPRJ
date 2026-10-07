package com.smartprocure.service.impl;

import com.smartprocure.dto.response.PriceSuggestionResponse;
import com.smartprocure.entity.PurchaseOrderItem;
import com.smartprocure.repository.PurchaseOrderItemRepository;
import com.smartprocure.repository.RequisitionItemRepository;
import com.smartprocure.service.SmartAssistanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI-powered Smart Requisition Assistance Service implementation.
 *
 * Provides item name autocomplete from historical procurement data (both
 * purchase orders and requisitions) and price suggestions based on the
 * median of recent purchases.
 */
@Service
@RequiredArgsConstructor
public class SmartAssistanceServiceImpl implements SmartAssistanceService {

    private static final int MAX_SUGGESTIONS = 10;
    private static final int MIN_PREFIX_LENGTH = 2;
    private static final int PRICE_HISTORY_LIMIT = 10;
    private static final int MIN_DATA_POINTS_FOR_SUGGESTION = 3;

    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final RequisitionItemRepository requisitionItemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<String> getItemSuggestions(String prefix) {
        if (prefix == null || prefix.length() < MIN_PREFIX_LENGTH) {
            return Collections.emptyList();
        }

        PageRequest pageRequest = PageRequest.of(0, MAX_SUGGESTIONS);

        // Query both repositories for distinct item names matching the prefix
        List<String> poItemNames = purchaseOrderItemRepository.findItemNamesByPrefix(prefix, pageRequest);
        List<String> reqItemNames = requisitionItemRepository.findItemNamesByPrefix(prefix, pageRequest);

        // Merge and deduplicate (case-insensitive), return up to 10 results
        return Stream.concat(poItemNames.stream(), reqItemNames.stream())
                .collect(Collectors.toCollection(() ->
                        new TreeSet<>(String.CASE_INSENSITIVE_ORDER)))
                .stream()
                .limit(MAX_SUGGESTIONS)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PriceSuggestionResponse getPriceSuggestion(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return PriceSuggestionResponse.builder()
                    .itemName(itemName)
                    .insufficientData(true)
                    .dataPointCount(0)
                    .build();
        }

        // Query last 10 purchases of matching item name, ordered by date descending
        PageRequest pageRequest = PageRequest.of(0, PRICE_HISTORY_LIMIT);
        List<PurchaseOrderItem> recentPurchases =
                purchaseOrderItemRepository.findByItemNameIgnoreCase(itemName, pageRequest);

        int dataPointCount = recentPurchases.size();

        // If fewer than 3 records, return insufficient data
        if (dataPointCount < MIN_DATA_POINTS_FOR_SUGGESTION) {
            return PriceSuggestionResponse.builder()
                    .itemName(itemName)
                    .insufficientData(true)
                    .dataPointCount(dataPointCount)
                    .build();
        }

        // Extract unit prices and compute statistics
        List<BigDecimal> prices = recentPurchases.stream()
                .map(PurchaseOrderItem::getUnitPrice)
                .sorted()
                .collect(Collectors.toList());

        BigDecimal medianPrice = computeMedian(prices);
        BigDecimal minPrice = prices.get(0);
        BigDecimal maxPrice = prices.get(prices.size() - 1);

        return PriceSuggestionResponse.builder()
                .itemName(itemName)
                .suggestedPrice(medianPrice)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .dataPointCount(dataPointCount)
                .insufficientData(false)
                .build();
    }

    /**
     * Computes the median of a sorted list of BigDecimal values.
     * For even-sized lists, returns the average of the two middle values.
     *
     * @param sortedPrices sorted list of prices (must not be empty)
     * @return the median value
     */
    private BigDecimal computeMedian(List<BigDecimal> sortedPrices) {
        int size = sortedPrices.size();
        int mid = size / 2;

        if (size % 2 == 0) {
            // Average of the two middle values
            BigDecimal sum = sortedPrices.get(mid - 1).add(sortedPrices.get(mid));
            return sum.divide(BigDecimal.valueOf(2), sortedPrices.get(0).scale(), java.math.RoundingMode.HALF_UP);
        } else {
            return sortedPrices.get(mid);
        }
    }
}
