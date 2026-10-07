package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CostCenterResponse {
    private Long id;
    private String name;
    private String code;
    private Long departmentId;
    private String departmentName;
}
