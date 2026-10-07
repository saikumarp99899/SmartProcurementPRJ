package com.smartprocure.service;

import com.smartprocure.dto.response.DemandForecastResponse;
import com.smartprocure.dto.response.SpendForecastResponse;

/**
 * Predictive analytics service that forecasts procurement spend and demand.
 *
 * Uses linear regression (trend) + seasonal adjustment to produce 6-month
 * forecasts with 80% confidence intervals. Applies minimum history guards
 * and low-confidence flagging based on rolling 3-month accuracy.
 */
public interface PredictiveAnalyticsService {

    /**
     * Generate a 6-month spend forecast based on historical PO data.
     * Uses linear trend + seasonal patterns with 80% confidence intervals.
     *
     * @return SpendForecastResponse containing forecasts and historical data
     */
    SpendForecastResponse getSpendForecast();

    /**
     * Generate demand forecasts for top 10 item categories by spend volume.
     * Each category gets 6 months of predicted demand (quantity + spend).
     * Categories with < 3 months of history are marked as insufficientData.
     *
     * @return DemandForecastResponse containing per-category demand forecasts
     */
    DemandForecastResponse getDemandForecast();
}
