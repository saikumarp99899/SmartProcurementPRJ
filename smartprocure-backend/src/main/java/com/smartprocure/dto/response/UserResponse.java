package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Safe user data returned to clients.
 * Password is intentionally NOT included here.
 */
@Getter
@Builder
public class UserResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String status;
    private Set<String> roles;
    /** Reporting manager, used by REQUESTER_MANAGER approval steps. */
    private Long managerId;
    private String managerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
