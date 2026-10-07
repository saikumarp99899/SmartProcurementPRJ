package com.smartprocure.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response body returned for all errors.
 *
 * Example:
 * {
 *   "timestamp": "2024-01-15T10:30:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Vendor not found with id: '999'",
 *   "path": "/api/vendors/999"
 * }
 */
@Getter
@Builder
public class ErrorResponse {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;

    private final int status;
    private final String error;
    private final String message;
    private final String path;

    // Optional: field-level validation errors
    private final Map<String, String> validationErrors;
}
