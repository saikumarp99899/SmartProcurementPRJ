package com.smartprocure.controller;

import com.smartprocure.dto.request.CreateVendorRequest;
import com.smartprocure.dto.request.UpdateVendorRequest;
import com.smartprocure.dto.response.PageResponse;
import com.smartprocure.dto.response.VendorResponse;
import com.smartprocure.entity.Vendor.VendorStatus;
import com.smartprocure.service.VendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vendors")
@RequiredArgsConstructor
@Tag(name = "Vendor Management", description = "Manage vendors")
public class VendorController {

    private final VendorService vendorService;

    /**
     * GET /api/vendors?keyword=tech&status=ACTIVE&page=0&size=10
     * Paginated, searchable, filterable vendor list.
     * Accessible by ADMIN and BUYER (buyers need to see vendors when creating POs).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER', 'APPROVER')")
    @Operation(summary = "Search vendors with pagination")
    public ResponseEntity<PageResponse<VendorResponse>> searchVendors(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VendorStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("vendorName").ascending());
        return ResponseEntity.ok(vendorService.searchVendors(keyword, status, pageable));
    }

    /**
     * GET /api/vendors/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER', 'APPROVER')")
    @Operation(summary = "Get vendor by ID")
    public ResponseEntity<VendorResponse> getVendorById(@PathVariable Long id) {
        return ResponseEntity.ok(vendorService.getVendorById(id));
    }

    /**
     * POST /api/vendors
     * Create new vendor. Admin only.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create vendor (Admin only)")
    public ResponseEntity<VendorResponse> createVendor(@Valid @RequestBody CreateVendorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vendorService.createVendor(request));
    }

    /**
     * PUT /api/vendors/{id}
     * Update vendor. Admin only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update vendor (Admin only)")
    public ResponseEntity<VendorResponse> updateVendor(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVendorRequest request) {
        return ResponseEntity.ok(vendorService.updateVendor(id, request));
    }

    /**
     * PATCH /api/vendors/{id}/deactivate
     * Deactivate a vendor. Admin only.
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate vendor (Admin only)")
    public ResponseEntity<VendorResponse> deactivateVendor(@PathVariable Long id) {
        return ResponseEntity.ok(vendorService.deactivateVendor(id));
    }
}
