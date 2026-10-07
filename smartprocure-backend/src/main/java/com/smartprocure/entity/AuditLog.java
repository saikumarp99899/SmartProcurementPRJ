package com.smartprocure.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tracks all important business actions in the system.
 * This is a write-only log — records are never updated, only inserted.
 *
 * entityType + entityId together identify which record was affected.
 * Example: entityType=VENDOR, entityId=5 means Vendor with ID 5.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    public enum AuditAction {
        LOGIN,
        USER_CREATED,
        USER_UPDATED,
        USER_ACTIVATED,
        USER_DEACTIVATED,
        VENDOR_CREATED,
        VENDOR_UPDATED,
        VENDOR_DEACTIVATED,
        REQUISITION_CREATED,
        REQUISITION_SUBMITTED,
        REQUISITION_APPROVED,
        REQUISITION_REJECTED,
        PO_CREATED,
        PO_UPDATED,
        PO_CANCELLED,
        PROXY_LOGIN_START,
        PROXY_LOGIN_END,
        PROXY_LOGIN_EXPIRED,
        PROXY_LOGIN_DENIED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Who performed the action. Nullable in case system performs it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * The type of action performed.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    /**
     * The type of entity affected: USER, VENDOR, REQUISITION, PO, etc.
     */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /**
     * The ID of the affected entity.
     */
    @Column(name = "entity_id")
    private Long entityId;

    /**
     * Human-readable description of what happened.
     */
    @Column(name = "description", length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
