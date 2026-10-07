package com.smartprocure.mapper;

import com.smartprocure.dto.response.AuditLogResponse;
import com.smartprocure.entity.AuditLog;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogResponse toResponse(AuditLog log) {
        if (log == null) return null;

        String userEmail = log.getUser() != null ? log.getUser().getEmail() : "SYSTEM";
        String userFullName = log.getUser() != null
                ? log.getUser().getFirstName() + " " + log.getUser().getLastName()
                : "SYSTEM";

        return AuditLogResponse.builder()
                .id(log.getId())
                .userEmail(userEmail)
                .userFullName(userFullName)
                .action(log.getAction().name())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .description(log.getDescription())
                .timestamp(log.getTimestamp())
                .build();
    }
}
