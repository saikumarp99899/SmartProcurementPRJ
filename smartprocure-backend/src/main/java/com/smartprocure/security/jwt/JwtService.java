package com.smartprocure.security.jwt;

import com.smartprocure.security.user.UserPrincipal;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles all JWT operations:
 * - generateToken: creates a signed JWT for a logged-in user
 * - generateProxyToken: creates a signed JWT for a proxy (impersonation) session
 * - extractUsername: reads the email (subject) from a token
 * - isTokenValid: validates signature + expiry
 * - extractUserId: reads the userId claim
 * - isProxyToken: checks if a token carries proxy claims
 * - extractAdminId: reads the adminId claim from a proxy token
 * - extractTargetUserId: reads the targetUserId claim from a proxy token
 *
 * JWT Structure:
 * Header.Payload.Signature
 * Header: {"alg": "HS256", "typ": "JWT"}
 * Payload: {"sub": "email", "id": 1, "roles": ["ROLE_ADMIN"], "iat": ..., "exp": ...}
 * Signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
 *
 * Proxy Token additional claims:
 * {"isProxy": true, "adminId": 1, "targetUserId": 2}
 */
@Service
@Slf4j
public class JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    /** Proxy token expiry: 60 minutes in milliseconds. */
    private static final long PROXY_TOKEN_EXPIRATION = 60 * 60 * 1000L;

    /**
     * Generates a JWT token for the authenticated user.
     * Claims stored in token: userId, email (subject), roles.
     */
    public String generateToken(UserPrincipal userPrincipal) {
        List<String> roles = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(userPrincipal.getEmail())
                .claim("id", userPrincipal.getId())
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generates a proxy JWT token for admin impersonation sessions.
     * The token is issued with the target user's email as subject and the target user's
     * roles for authorization, but also carries the admin's identity in claims for audit.
     *
     * Proxy tokens have an independent 60-minute expiry regardless of the normal token expiry setting.
     *
     * @param targetUserPrincipal the target user being impersonated (provides subject and roles)
     * @param adminId the ID of the ADMIN who initiated the proxy session
     * @return a signed JWT string with proxy claims
     */
    public String generateProxyToken(UserPrincipal targetUserPrincipal, Long adminId) {
        List<String> roles = targetUserPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(targetUserPrincipal.getEmail())
                .claim("id", targetUserPrincipal.getId())
                .claim("roles", roles)
                .claim("isProxy", true)
                .claim("adminId", adminId)
                .claim("targetUserId", targetUserPrincipal.getId())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + PROXY_TOKEN_EXPIRATION))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts the email (subject) from the token.
     * Spring Security uses this to load the user during each request.
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Extracts the userId claim from the token.
     * Useful to avoid an extra DB call when we just need the user ID.
     */
    public Long extractUserId(String token) {
        return extractAllClaims(token).get("id", Long.class);
    }

    /**
     * Checks if the token is a proxy token (contains isProxy = true claim).
     *
     * @param token the JWT string
     * @return true if the token carries proxy claims, false otherwise
     */
    public boolean isProxyToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Boolean isProxy = claims.get("isProxy", Boolean.class);
            return Boolean.TRUE.equals(isProxy);
        } catch (JwtException e) {
            log.warn("Failed to check proxy status for token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts the adminId claim from a proxy token.
     * This identifies the ADMIN who initiated the proxy session.
     *
     * @param token the JWT string (should be a proxy token)
     * @return the admin user ID, or null if the claim is not present
     */
    public Long extractAdminId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("adminId", Long.class);
    }

    /**
     * Extracts the targetUserId claim from a proxy token.
     * This identifies the user being impersonated.
     *
     * @param token the JWT string (should be a proxy token)
     * @return the target user ID, or null if the claim is not present
     */
    public Long extractTargetUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("targetUserId", Long.class);
    }

    /**
     * Validates the token:
     * 1. Signature is valid (not tampered)
     * 2. Token is not expired
     * 3. Username in token matches the UserDetails
     *
     * Works for both normal and proxy tokens.
     */
    public boolean isTokenValid(String token, UserPrincipal userPrincipal) {
        try {
            final String username = extractUsername(token);
            return username.equals(userPrincipal.getUsername()) && !isTokenExpired(token);
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Converts the Base64-encoded secret key string into an HMAC-SHA key object.
     * The secret key must be at least 256 bits (32 bytes) for HS256.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
