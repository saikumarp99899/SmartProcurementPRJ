package com.smartprocure.security.jwt;

import com.smartprocure.security.user.CustomUserDetailsService;
import com.smartprocure.security.user.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Intercepts every HTTP request and validates the JWT token.
 *
 * Execution flow for every request:
 * 1. Extract "Authorization" header
 * 2. Check if it starts with "Bearer "
 * 3. Extract the token (remove "Bearer " prefix)
 * 4. Extract username (email) from token
 * 5. Load UserDetails from DB
 * 6. Validate token (signature + expiry + username match)
 * 7. If valid, set Authentication in SecurityContext → request is authenticated
 * 8. If invalid/missing, continue without setting auth → Spring Security blocks protected endpoints
 *
 * For proxy tokens:
 * - The subject is the target user's email (impersonated user)
 * - Authentication is established as the target user (for role-based access)
 * - The admin's identity is preserved in the request attribute "proxyAdminId"
 * - A request attribute "isProxySession" is set to true
 *
 * OncePerRequestFilter guarantees this filter runs exactly once per request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    /** Request attribute key indicating if the current request is a proxy session. */
    public static final String PROXY_SESSION_ATTR = "isProxySession";

    /** Request attribute key holding the admin user ID for proxy sessions. */
    public static final String PROXY_ADMIN_ID_ATTR = "proxyAdminId";

    /** Request attribute key holding the target user ID for proxy sessions. */
    public static final String PROXY_TARGET_USER_ID_ATTR = "proxyTargetUserId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Skip filter for public auth endpoints (login/register don't need JWT)
        final String path = request.getServletPath();
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        // No Authorization header or doesn't start with "Bearer " → skip
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract token: "Bearer eyJhbGci..." → "eyJhbGci..."
        final String jwt = authHeader.substring(7);

        try {
            final String userEmail = jwtService.extractUsername(jwt);

            // Only authenticate if we have an email and no existing auth in context
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserPrincipal userPrincipal = (UserPrincipal) userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.isTokenValid(jwt, userPrincipal)) {
                    // Create authentication token and set it in Spring Security context
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userPrincipal,
                                    null,
                                    userPrincipal.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // If this is a proxy token, set request attributes for dual identity
                    if (jwtService.isProxyToken(jwt)) {
                        Long adminId = jwtService.extractAdminId(jwt);
                        Long targetUserId = jwtService.extractTargetUserId(jwt);
                        request.setAttribute(PROXY_SESSION_ATTR, true);
                        request.setAttribute(PROXY_ADMIN_ID_ATTR, adminId);
                        request.setAttribute(PROXY_TARGET_USER_ID_ATTR, targetUserId);
                        log.debug("Proxy session authenticated: admin={} impersonating user={} ({})",
                                adminId, targetUserId, userEmail);
                    } else {
                        log.debug("Authenticated user: {}", userEmail);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("JWT authentication failed for request {}: {}", path, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
