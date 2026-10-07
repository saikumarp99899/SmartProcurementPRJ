package com.smartprocure.controller;

import com.smartprocure.audit.AuditLogService;
import com.smartprocure.dto.response.AuditLogResponse;
import com.smartprocure.dto.response.PageResponse;
import com.smartprocure.entity.AuditLog;
import com.smartprocure.mapper.AuditLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "View system audit logs (Admin only)")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final AuditLogMapper auditLogMapper;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all audit logs (Admin only)")
    public ResponseEntity<PageResponse<AuditLogResponse>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> logPage = auditLogService.getAllLogs(pageable);

        List<AuditLogResponse> data = logPage.getContent().stream()
                .map(auditLogMapper::toResponse)
                .collect(Collectors.toList());

        PageResponse<AuditLogResponse> response = PageResponse.<AuditLogResponse>builder()
                .data(data)
                .currentPage(logPage.getNumber())
                .totalItems(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .pageSize(logPage.getSize())
                .last(logPage.isLast())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit logs for a specific entity")
    public ResponseEntity<PageResponse<AuditLogResponse>> getLogsByEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> logPage = auditLogService.getLogsByEntity(entityType.toUpperCase(), entityId, pageable);

        List<AuditLogResponse> data = logPage.getContent().stream()
                .map(auditLogMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(PageResponse.<AuditLogResponse>builder()
                .data(data)
                .currentPage(logPage.getNumber())
                .totalItems(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .pageSize(logPage.getSize())
                .last(logPage.isLast())
                .build());
    }
}
