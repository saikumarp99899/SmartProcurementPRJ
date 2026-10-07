package com.smartprocure.service;

import com.smartprocure.dto.response.AnomalyResponse;

import java.util.List;

/**
 * Detects anomalies in procurement data:
 * - Price anomalies (items priced > 2σ above historical mean)
 * - Split ordering patterns (multiple below-threshold requisitions)
 * - Vendor concentration risks (single vendor > 60% of category spend)
 */
public interface AnomalyDetectionService {
    List<AnomalyResponse> detectAnomalies();
}
