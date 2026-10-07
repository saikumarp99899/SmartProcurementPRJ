package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Generic paginated response wrapper.
 * Used for any paginated list endpoint.
 *
 * Example JSON:
 * {
 *   "data": [...],
 *   "currentPage": 0,
 *   "totalItems": 50,
 *   "totalPages": 5,
 *   "pageSize": 10
 * }
 *
 * T is a generic type parameter — same class works for vendors, requisitions, etc.
 */
@Getter
@Builder
public class PageResponse<T> {
    private List<T> data;
    private int currentPage;
    private long totalItems;
    private int totalPages;
    private int pageSize;
    private boolean last;
}
