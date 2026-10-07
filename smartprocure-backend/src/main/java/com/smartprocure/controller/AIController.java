package com.smartprocure.controller;

import com.smartprocure.dto.response.AnomalyResponse;
import com.smartprocure.dto.response.DemandForecastResponse;
import com.smartprocure.dto.response.PriceSuggestionResponse;
import com.smartprocure.dto.response.SpendForecastResponse;
import com.smartprocure.dto.response.VendorRecommendationResponse;
import com.smartprocure.service.AnomalyDetectionService;
import com.smartprocure.service.PredictiveAnalyticsService;
import com.smartprocure.service.SmartAssistanceService;
import com.smartprocure.service.VendorRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI-powered endpoints for vendor recommendations, anomaly detection,
 * predictive analytics, and smart requisition assistance.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
@Tag(name = "AI Services", description = "AI-powered vendor recommendations, anomaly detection, predictive analytics, and smart assistance")
public class AIController {

    private final VendorRecommendationService vendorRecommendationService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final PredictiveAnalyticsService predictiveAnalyticsService;
    private final SmartAssistanceService smartAssistanceService;

    /**
     * GET /api/ai/vendor-recommendations?itemName={name}
     * Returns up to 5 recommended vendors for the given item name,
     * ranked by composite score (pricing, delivery, quality).
     */
    @GetMapping("/vendor-recommendations")
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN')")
    @Operation(summary = "Get AI-powered vendor recommendations for an item")
    public ResponseEntity<List<VendorRecommendationResponse>> getVendorRecommendations(
            @RequestParam @Size(min = 3, message = "Item name must be at least 3 characters") String itemName) {
        return ResponseEntity.ok(vendorRecommendationService.getRecommendations(itemName));
    }

    /**
     * GET /api/ai/anomalies
     * Returns detected procurement anomalies (price, split ordering, vendor concentration)
     * with severity levels and recommended actions. ADMIN only.
     */
    @GetMapping("/anomalies")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get detected procurement anomalies")
    public ResponseEntity<List<AnomalyResponse>> getAnomalies() {
        return ResponseEntity.ok(anomalyDetectionService.detectAnomalies());
    }

    /**
     * GET /api/ai/predictions/spend-forecast
     * Returns 6-month spend forecasts with 80% confidence intervals
     * based on historical procurement trends.
     */
    @GetMapping("/predictions/spend-forecast")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER')")
    @Operation(summary = "Get spend forecast predictions for the next 6 months")
    public ResponseEntity<SpendForecastResponse> getSpendForecast() {
        return ResponseEntity.ok(predictiveAnalyticsService.getSpendForecast());
    }

    /**
     * GET /api/ai/predictions/demand
     * Returns demand forecasts for the top 10 item categories
     * with seasonal patterns and confidence intervals.
     */
    @GetMapping("/predictions/demand")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER')")
    @Operation(summary = "Get demand forecast predictions by category")
    public ResponseEntity<DemandForecastResponse> getDemandForecast() {
        return ResponseEntity.ok(predictiveAnalyticsService.getDemandForecast());
    }

    /**
     * GET /api/ai/item-suggestions?prefix={text}
     * Returns up to 10 item name suggestions from historical data
     * matching the given prefix (case-insensitive).
     */
    @GetMapping("/item-suggestions")
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN')")
    @Operation(summary = "Get item name autocomplete suggestions")
    public ResponseEntity<List<String>> getItemSuggestions(@RequestParam String prefix) {
        return ResponseEntity.ok(smartAssistanceService.getItemSuggestions(prefix));
    }

    /**
     * GET /api/ai/price-suggestion?itemName={name}
     * Returns a suggested unit price based on the median of recent purchases,
     * along with the price range and data point count.
     */
    @GetMapping("/price-suggestion")
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN')")
    @Operation(summary = "Get AI-suggested price for an item based on historical data")
    public ResponseEntity<PriceSuggestionResponse> getPriceSuggestion(@RequestParam String itemName) {
        return ResponseEntity.ok(smartAssistanceService.getPriceSuggestion(itemName));
    }
}
