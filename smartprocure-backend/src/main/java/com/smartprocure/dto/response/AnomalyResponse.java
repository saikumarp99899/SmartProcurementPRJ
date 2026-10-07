package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Detected procurement anomaly with severity, description, and affected entities.
 * Types: PRICE, SPLIT_ORDER, VENDOR_CONCENTRATION
 * Severity levels: LOW, MEDIUM, HIGH
 */
@Getter
@Builder
public class AnomalyResponse {

    private Long id;
    private String type;
    private String severity;
    private String description;
    private List<AffectedEntity> affectedEntities;
    private String recommendedAction;
    private LocalDateTime detectedAt;
}
