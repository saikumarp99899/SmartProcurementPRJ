package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AuditLogResponse {
    private Long id;
    private String userEmail;
    private String userFullName;
    private String action;
    private String entityType;
    private Long entityId;
    private String description;
    private LocalDateTime timestamp;
}
