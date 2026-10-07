package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * AI-generated price suggestion for a requisition item.
 * Based on median of recent historical purchases.
 */
@Getter
@Builder
public class PriceSuggestionResponse {

    private String itemName;
    private BigDecimal suggestedPrice;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private int dataPointCount;
    private boolean insufficientData;
}
