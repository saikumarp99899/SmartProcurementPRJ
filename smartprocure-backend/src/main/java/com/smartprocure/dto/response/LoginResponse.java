package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**
 * Returned after successful login.
 * Contains the JWT token and user info so the frontend can store and use them.
 */
@Getter
@Builder
public class LoginResponse {
    private String accessToken;
    private String tokenType;   // Always "Bearer"
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
}
