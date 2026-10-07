package com.smartprocure.controller;

import com.smartprocure.entity.CompanyAddress;
import com.smartprocure.entity.CompanyAddress.AddressType;
import com.smartprocure.repository.CompanyAddressRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/company-addresses")
@RequiredArgsConstructor
@Tag(name = "Company Addresses", description = "Manage ship-to and bill-to company addresses")
public class CompanyAddressController {

    private final CompanyAddressRepository companyAddressRepository;

    /**
     * GET /api/company-addresses?type=SHIP_TO
     * Returns addresses filtered by type, or all if type is not specified.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER', 'APPROVER')")
    @Operation(summary = "Get company addresses, optionally filtered by type")
    public ResponseEntity<List<CompanyAddress>> getAddresses(
            @RequestParam(required = false) AddressType type) {

        List<CompanyAddress> addresses;
        if (type != null) {
            addresses = companyAddressRepository.findByAddressType(type);
        } else {
            addresses = companyAddressRepository.findAll();
        }
        return ResponseEntity.ok(addresses);
    }
}
