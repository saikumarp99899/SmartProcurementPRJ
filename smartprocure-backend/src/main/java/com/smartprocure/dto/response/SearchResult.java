package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Individual search result entry with common display fields.
 */
@Getter
@Builder
public class SearchResult {

    private Long id;
    private String title;
    private String subtitle;
    private String entityType;
}
