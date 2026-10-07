package com.smartprocure.service;

import com.smartprocure.dto.response.ProxyLoginResponse;

/**
 * Service for managing admin proxy login (impersonation) sessions.
 * Allows ADMIN users to operate the system as another user for troubleshooting.
 */
public interface ProxyLoginService {

    /**
     * Initiates a proxy login session for the given admin to impersonate the target user.
     *
     * @param adminId       the ID of the ADMIN user initiating the session
     * @param targetUserId  the ID of the user to impersonate
     * @return proxy login response with token and target user info
     */
    ProxyLoginResponse initiateProxyLogin(Long adminId, Long targetUserId);

    /**
     * Ends an active proxy session for the given admin user.
     * Marks the session as ENDED and sets the endedAt timestamp.
     *
     * @param adminId the ID of the ADMIN user whose proxy session should be ended
     */
    void endProxySession(Long adminId);

    /**
     * Checks whether a given JWT token represents a proxy session.
     *
     * @param token the JWT token to check
     * @return true if the token contains proxy claims, false otherwise
     */
    boolean isProxySession(String token);
}
