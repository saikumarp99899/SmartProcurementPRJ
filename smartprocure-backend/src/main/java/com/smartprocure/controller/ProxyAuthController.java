package com.smartprocure.controller;

import com.smartprocure.dto.request.ProxyLoginRequest;
import com.smartprocure.dto.response.ProxyLoginResponse;
import com.smartprocure.security.user.UserPrincipal;
import com.smartprocure.service.ProxyLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for admin proxy login (impersonation) operations.
 *
 * Proxy login allows ADMIN users to operate the system as another user
 * for troubleshooting, permission verification, and user assistance.
 *
 * Endpoints:
 * - POST /api/auth/proxy-login  — Start a proxy session (ADMIN only)
 * - POST /api/auth/proxy-logout — End an active proxy session
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Proxy Authentication", description = "Admin proxy login (impersonation) APIs")
public class ProxyAuthController {

    private final ProxyLoginService proxyLoginService;

    /**
     * POST /api/auth/proxy-login
     *
     * Initiates a proxy login session allowing an ADMIN to impersonate a target user.
     * The target user must be active, non-ADMIN, and there must be no existing active
     * proxy session for the admin.
     *
     * @param request the proxy login request containing the target user ID
     * @param admin   the authenticated ADMIN user initiating the proxy session
     * @return proxy login response with token and target user profile
     */
    @PostMapping("/proxy-login")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Start proxy login session (Admin only)")
    public ResponseEntity<ProxyLoginResponse> proxyLogin(
            @Valid @RequestBody ProxyLoginRequest request,
            @AuthenticationPrincipal UserPrincipal admin) {
        ProxyLoginResponse response = proxyLoginService.initiateProxyLogin(
                admin.getId(), request.getTargetUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/proxy-logout
     *
     * Ends the active proxy session for the current user.
     * After this call, the admin's original session is restored.
     *
     * When called from a proxy session, the admin ID is extracted from the
     * proxy token claims (set as a request attribute by JwtAuthenticationFilter).
     * Falls back to currentUser.getId() if not in a proxy session.
     *
     * @param currentUser the authenticated user ending the proxy session
     * @param request the HTTP request containing proxy session attributes
     * @return 204 No Content on successful session termination
     */
    @PostMapping("/proxy-logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "End active proxy session")
    public ResponseEntity<Void> endProxySession(
            @AuthenticationPrincipal UserPrincipal currentUser,
            jakarta.servlet.http.HttpServletRequest request) {
        // In a proxy session, the authenticated user is the target (impersonated) user.
        // We need the admin's ID to find the proxy session record.
        Object proxyAdminId = request.getAttribute("proxyAdminId");
        Long adminId = proxyAdminId != null ? ((Number) proxyAdminId).longValue() : currentUser.getId();
        proxyLoginService.endProxySession(adminId);
        return ResponseEntity.noContent().build();
    }
}
