package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Demand forecast for a single item category, including
 * future predictions and historical data.
 */
@Getter
@Builder
public class CategoryDemandForecast {

    private String categoryName;
    private List<MonthlyDemand> forecasts;
    private List<MonthlyDemand> historical;
    private boolean insufficientData;
}
