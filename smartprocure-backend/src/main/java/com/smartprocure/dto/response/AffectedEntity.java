package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * An entity affected by a detected anomaly.
 */
@Getter
@Builder
public class AffectedEntity {

    private String entityType;
    private Long entityId;
    private String entityName;
}
