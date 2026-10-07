package com.smartprocure.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApprovalActionRequest {

    /**
     * Comments are optional for approval, required for rejection.
     * The service layer enforces the "required on rejection" business rule.
     */
    @Size(max = 1000)
    private String comments;
}
