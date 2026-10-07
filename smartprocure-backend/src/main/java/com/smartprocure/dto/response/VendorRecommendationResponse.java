package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * AI-generated vendor recommendation for a requisition item.
 * Includes a composite confidence score and reasoning.
 */
@Getter
@Builder
public class VendorRecommendationResponse {

    private Long vendorId;
    private String vendorName;
    private String vendorCode;
    private int confidenceScore;        // 0-100
    private BigDecimal averagePrice;
    private int deliveryPerformance;    // 0-100 percentage
    private BigDecimal rating;
    private String reasoning;
}
