package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * A single month's historical spend data point.
 */
@Getter
@Builder
public class HistoricalDataPoint {

    private String month;
    private BigDecimal actualSpend;
    private int orderCount;
}
