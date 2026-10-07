package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * A single month's demand forecast or historical demand data.
 */
@Getter
@Builder
public class MonthlyDemand {

    private String month;
    private int predictedQuantity;
    private BigDecimal predictedSpend;
    private BigDecimal lowerBound;
    private BigDecimal upperBound;
}
