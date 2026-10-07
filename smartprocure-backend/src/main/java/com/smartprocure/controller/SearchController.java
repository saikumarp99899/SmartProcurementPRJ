package com.smartprocure.controller;

import com.smartprocure.dto.response.SearchResponse;
import com.smartprocure.security.user.UserPrincipal;
import com.smartprocure.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
@Tag(name = "Global Search", description = "Cross-entity search across requisitions, purchase orders, vendors, and items")
public class SearchController {

    private final SearchService searchService;

    /**
     * GET /api/search?q={query}
     * Performs cross-entity search filtered by the authenticated user's role permissions.
     * Query must be between 2 and 100 characters.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search across all entities")
    public ResponseEntity<SearchResponse> search(
            @RequestParam @Size(min = 2, max = 100, message = "Query must be between 2 and 100 characters") String q,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(searchService.search(q, currentUser));
    }
}
