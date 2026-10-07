package com.smartprocure.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    // Admin can update roles via this field
    private String role;

    /**
     * Reporting manager, used by REQUESTER_MANAGER approval steps.
     * Send 0 to clear an existing manager, since null means "leave unchanged".
     */
    private Long managerId;
}
