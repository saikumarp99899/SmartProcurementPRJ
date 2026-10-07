package com.smartprocure.service.impl;

import com.smartprocure.audit.AuditLogService;
import com.smartprocure.dto.request.CreateVendorRequest;
import com.smartprocure.dto.request.UpdateVendorRequest;
import com.smartprocure.dto.response.PageResponse;
import com.smartprocure.dto.response.VendorResponse;
import com.smartprocure.entity.AuditLog.AuditAction;
import com.smartprocure.entity.Vendor;
import com.smartprocure.entity.Vendor.VendorStatus;
import com.smartprocure.exception.DuplicateResourceException;
import com.smartprocure.exception.ResourceNotFoundException;
import com.smartprocure.mapper.VendorMapper;
import com.smartprocure.repository.VendorRepository;
import com.smartprocure.service.VendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorServiceImpl implements VendorService {

    private final VendorRepository vendorRepository;
    private final VendorMapper vendorMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public VendorResponse createVendor(CreateVendorRequest request) {
        // Business rule: vendorCode must be unique
        if (vendorRepository.existsByVendorCode(request.getVendorCode())) {
            throw new DuplicateResourceException(
                "Vendor code already exists: " + request.getVendorCode());
        }
        if (vendorRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                "Vendor email already exists: " + request.getEmail());
        }

        Vendor vendor = Vendor.builder()
                .vendorCode(request.getVendorCode().toUpperCase())
                .vendorName(request.getVendorName())
                .email(request.getEmail().toLowerCase())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .rating(request.getRating())
                .status(VendorStatus.ACTIVE)
                .build();

        Vendor saved = vendorRepository.save(vendor);
        log.info("Vendor created: {}", saved.getVendorCode());

        auditLogService.log(null, AuditAction.VENDOR_CREATED, "VENDOR", saved.getId(),
                "Vendor created: " + saved.getVendorName() + " (" + saved.getVendorCode() + ")");

        return vendorMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VendorResponse getVendorById(Long id) {
        log.info("Fetching vendor by id: {}", id);
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Vendor not found with id: {}", id);
                    return new ResourceNotFoundException("Vendor", "id", id);
                });
        return vendorMapper.toResponse(vendor);
    }

    @Override
    @Transactional
    public VendorResponse updateVendor(Long id, UpdateVendorRequest request) {
        log.info("Updating vendor id: {}", id);
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Vendor not found for update: id={}", id);
                    return new ResourceNotFoundException("Vendor", "id", id);
                });

        if (request.getVendorName() != null) vendor.setVendorName(request.getVendorName());
        if (request.getEmail() != null) vendor.setEmail(request.getEmail().toLowerCase());
        if (request.getPhone() != null) vendor.setPhone(request.getPhone());
        if (request.getAddress() != null) vendor.setAddress(request.getAddress());
        if (request.getCity() != null) vendor.setCity(request.getCity());
        if (request.getState() != null) vendor.setState(request.getState());
        if (request.getCountry() != null) vendor.setCountry(request.getCountry());
        if (request.getRating() != null) vendor.setRating(request.getRating());

        Vendor updated = vendorRepository.save(vendor);
        log.info("Vendor updated: {} (id={})", updated.getVendorCode(), id);
        auditLogService.log(null, AuditAction.VENDOR_UPDATED, "VENDOR", updated.getId(),
                "Vendor updated: " + updated.getVendorCode());

        return vendorMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public VendorResponse deactivateVendor(Long id) {
        log.info("Deactivating vendor id: {}", id);
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Vendor not found for deactivation: id={}", id);
                    return new ResourceNotFoundException("Vendor", "id", id);
                });
        vendor.setStatus(VendorStatus.INACTIVE);
        Vendor updated = vendorRepository.save(vendor);
        log.info("Vendor deactivated: {} (id={})", updated.getVendorCode(), id);
        auditLogService.log(null, AuditAction.VENDOR_DEACTIVATED, "VENDOR", updated.getId(),
                "Vendor deactivated: " + updated.getVendorCode());
        return vendorMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VendorResponse> searchVendors(String keyword, VendorStatus status, Pageable pageable) {
        log.info("Searching vendors [keyword={}, status={}, page={}]", keyword, status, pageable.getPageNumber());
        Page<Vendor> page = vendorRepository.searchVendors(keyword, status, pageable);
        log.info("Found {} vendors matching criteria", page.getTotalElements());

        List<VendorResponse> data = page.getContent().stream()
                .map(vendorMapper::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<VendorResponse>builder()
                .data(data)
                .currentPage(page.getNumber())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageSize(page.getSize())
                .last(page.isLast())
                .build();
    }
}
