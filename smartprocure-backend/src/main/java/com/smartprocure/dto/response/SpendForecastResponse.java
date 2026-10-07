package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Spend forecast response containing predicted monthly spend and historical data.
 */
@Getter
@Builder
public class SpendForecastResponse {

    private List<MonthlyForecast> forecasts;
    private List<HistoricalDataPoint> historicalData;
}
