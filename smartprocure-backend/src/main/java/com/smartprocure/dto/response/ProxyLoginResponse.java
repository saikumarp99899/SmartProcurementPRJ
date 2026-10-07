package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Response after successful proxy login.
 * Contains the proxy token and target user profile information.
 */
@Getter
@Builder
public class ProxyLoginResponse {

    private String proxyToken;
    private String tokenType;
    private Long targetUserId;
    private String targetEmail;
    private String targetFirstName;
    private String targetLastName;
    private Set<String> targetRoles;
    private LocalDateTime expiresAt;
}
