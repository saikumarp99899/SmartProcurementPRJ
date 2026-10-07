package com.smartprocure.controller;

import com.smartprocure.dto.response.AdminDashboardResponse;
import com.smartprocure.dto.response.ApproverDashboardResponse;
import com.smartprocure.dto.response.BuyerDashboardResponse;
import com.smartprocure.security.user.UserPrincipal;
import com.smartprocure.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard summary APIs")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin dashboard summary")
    public ResponseEntity<AdminDashboardResponse> getAdminDashboard() {
        return ResponseEntity.ok(dashboardService.getAdminDashboard());
    }

    @GetMapping("/buyer")
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN')")
    @Operation(summary = "Buyer dashboard summary")
    public ResponseEntity<BuyerDashboardResponse> getBuyerDashboard(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(dashboardService.getBuyerDashboard(currentUser.getId()));
    }

    @GetMapping("/approver")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    @Operation(summary = "Approver dashboard summary")
    public ResponseEntity<ApproverDashboardResponse> getApproverDashboard(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(dashboardService.getApproverDashboard(currentUser.getId()));
    }
}
