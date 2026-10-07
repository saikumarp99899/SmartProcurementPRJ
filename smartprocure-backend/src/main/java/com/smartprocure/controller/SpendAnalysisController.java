package com.smartprocure.controller;

import com.smartprocure.dto.response.SpendAnalysisResponse;
import com.smartprocure.service.SpendAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-powered Spend Analysis endpoint.
 * Provides intelligent procurement analytics, insights, and recommendations.
 */
@RestController
@RequestMapping("/api/ai/spend-analysis")
@RequiredArgsConstructor
@Tag(name = "AI Analytics", description = "AI-powered procurement analytics and insights")
public class SpendAnalysisController {

    private final SpendAnalysisService spendAnalysisService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER')")
    @Operation(summary = "Get AI spend analysis with insights and recommendations")
    public ResponseEntity<SpendAnalysisResponse> getSpendAnalysis() {
        return ResponseEntity.ok(spendAnalysisService.getSpendAnalysis());
    }
}
