package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Demand forecast response containing per-category demand predictions.
 */
@Getter
@Builder
public class DemandForecastResponse {

    private List<CategoryDemandForecast> categories;
}
