package com.smartprocure.mapper;

import com.smartprocure.dto.response.VendorResponse;
import com.smartprocure.entity.Vendor;
import org.springframework.stereotype.Component;

@Component
public class VendorMapper {

    public VendorResponse toResponse(Vendor vendor) {
        if (vendor == null) return null;

        return VendorResponse.builder()
                .id(vendor.getId())
                .vendorCode(vendor.getVendorCode())
                .vendorName(vendor.getVendorName())
                .email(vendor.getEmail())
                .phone(vendor.getPhone())
                .address(vendor.getAddress())
                .city(vendor.getCity())
                .state(vendor.getState())
                .country(vendor.getCountry())
                .status(vendor.getStatus().name())
                .rating(vendor.getRating())
                .createdAt(vendor.getCreatedAt())
                .updatedAt(vendor.getUpdatedAt())
                .build();
    }
}
