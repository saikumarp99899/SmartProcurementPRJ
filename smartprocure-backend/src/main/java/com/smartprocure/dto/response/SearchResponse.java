package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Response for global search across entities.
 * Groups results by entity type for dropdown display.
 */
@Getter
@Builder
public class SearchResponse {

    private List<SearchResult> requisitions;
    private List<SearchResult> purchaseOrders;
    private List<SearchResult> vendors;
    private List<SearchResult> items;
}
