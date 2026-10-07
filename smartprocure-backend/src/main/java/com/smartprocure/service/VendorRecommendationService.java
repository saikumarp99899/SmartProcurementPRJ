package com.smartprocure.service;

import com.smartprocure.dto.response.VendorRecommendationResponse;

import java.util.List;

/**
 * AI-powered Vendor Recommendation Service.
 * Suggests optimal vendors for requisition items based on
 * historical pricing, delivery performance, and quality ratings.
 */
public interface VendorRecommendationService {
    List<VendorRecommendationResponse> getRecommendations(String itemName);
}
