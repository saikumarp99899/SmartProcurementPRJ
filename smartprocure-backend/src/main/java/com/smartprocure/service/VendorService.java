package com.smartprocure.service;

import com.smartprocure.dto.request.CreateVendorRequest;
import com.smartprocure.dto.request.UpdateVendorRequest;
import com.smartprocure.dto.response.PageResponse;
import com.smartprocure.dto.response.VendorResponse;
import com.smartprocure.entity.Vendor.VendorStatus;
import org.springframework.data.domain.Pageable;

public interface VendorService {
    VendorResponse createVendor(CreateVendorRequest request);
    VendorResponse getVendorById(Long id);
    VendorResponse updateVendor(Long id, UpdateVendorRequest request);
    VendorResponse deactivateVendor(Long id);
    PageResponse<VendorResponse> searchVendors(String keyword, VendorStatus status, Pageable pageable);
}
