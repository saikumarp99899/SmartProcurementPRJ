package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * A single month's spend forecast with confidence interval bounds.
 */
@Getter
@Builder
public class MonthlyForecast {

    private String month;               // e.g. "2025-07"
    private BigDecimal predictedSpend;
    private BigDecimal lowerBound;      // 80% confidence
    private BigDecimal upperBound;
    private boolean lowConfidence;
}
