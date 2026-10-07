package com.smartprocure.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a business rule is violated.
 * Example: Creating a PO from a REJECTED requisition.
 * Maps to HTTP 422 Unprocessable Entity — the request is syntactically correct
 * but violates business logic.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
