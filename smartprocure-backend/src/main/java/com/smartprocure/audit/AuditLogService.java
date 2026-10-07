package com.smartprocure.audit;

import com.smartprocure.entity.AuditLog;
import com.smartprocure.entity.AuditLog.AuditAction;
import com.smartprocure.entity.User;
import com.smartprocure.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Centralized service for recording audit events.
 *
 * Why @Async?
 * Audit logging should not slow down the main business operation.
 * For example, when creating a vendor, we don't want to wait for the audit log
 * to be saved before returning the API response.
 *
 * With @Async, the log() method runs in a separate thread pool thread.
 *
 * Note: For @Async to work, you need @EnableAsync on a configuration class.
 * We add it to the main application class.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Records a business action in the audit log.
     *
     * @param user        the user who performed the action (can be null for system actions)
     * @param action      the type of action (enum)
     * @param entityType  the type of entity affected ("VENDOR", "REQUISITION", etc.)
     * @param entityId    the ID of the affected entity
     * @param description a human-readable description
     */
    @Async
    public void log(User user, AuditAction action, String entityType, Long entityId, String description) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Audit logging failure should NEVER break the main operation
            log.error("Failed to save audit log for action {}: {}", action, e.getMessage());
        }
    }

    public Page<AuditLog> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    public Page<AuditLog> getLogsByUser(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable);
    }

    public Page<AuditLog> getLogsByEntity(String entityType, Long entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
    }
}
