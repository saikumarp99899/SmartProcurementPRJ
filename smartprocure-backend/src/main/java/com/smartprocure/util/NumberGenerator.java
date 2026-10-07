package com.smartprocure.util;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates unique, human-readable reference numbers.
 *
 * Examples:
 * - REQ-2024-00001
 * - PO-2024-00001
 *
 * Note: In production with high concurrency, you'd use a DB sequence.
 * For Phase 1 portfolio project, this is sufficient.
 * The actual uniqueness is enforced by the DB unique constraint on the column.
 */
@Component
public class NumberGenerator {

    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    /**
     * Generates a requisition number.
     * Uses current timestamp in milliseconds as suffix to ensure uniqueness.
     * Pattern: REQ-2024-1704067200000
     */
    public String generateRequisitionNumber() {
        String year = LocalDateTime.now().format(YEAR_FORMAT);
        long suffix = System.currentTimeMillis();
        return String.format("REQ-%s-%d", year, suffix);
    }

    /**
     * Generates a purchase order number.
     * Pattern: PO-2024-1704067200000
     */
    public String generatePONumber() {
        String year = LocalDateTime.now().format(YEAR_FORMAT);
        long suffix = System.currentTimeMillis();
        return String.format("PO-%s-%d", year, suffix);
    }
}
