package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class VendorResponse {
    private Long id;
    private String vendorCode;
    private String vendorName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String country;
    private String status;
    private BigDecimal rating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
