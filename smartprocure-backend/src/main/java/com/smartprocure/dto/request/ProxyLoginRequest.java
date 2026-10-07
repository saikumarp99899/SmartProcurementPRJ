package com.smartprocure.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for admin proxy login.
 * Contains the target user ID to impersonate.
 */
@Getter
@Setter
public class ProxyLoginRequest {

    @NotNull(message = "Target user ID is required")
    private Long targetUserId;
}
