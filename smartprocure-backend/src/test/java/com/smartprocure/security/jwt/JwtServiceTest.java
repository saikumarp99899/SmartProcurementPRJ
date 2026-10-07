package com.smartprocure.security.jwt;

import com.smartprocure.entity.Role;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.entity.User;
import com.smartprocure.security.user.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JwtService covering both normal and proxy token operations.
 */
class JwtServiceTest {

    private JwtService jwtService;

    // Base64-encoded 256-bit key (same as used in test properties)
    private static final String TEST_SECRET_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long TEST_EXPIRATION = 86400000L; // 24 hours

    private UserPrincipal adminPrincipal;
    private UserPrincipal buyerPrincipal;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET_KEY);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", TEST_EXPIRATION);

        // Create an ADMIN user
        Role adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setName(RoleName.ADMIN);

        User adminUser = User.builder()
                .id(1L)
                .firstName("Admin")
                .lastName("User")
                .email("admin@smartprocure.com")
                .password("encoded-password")
                .status(User.UserStatus.ACTIVE)
                .roles(Set.of(adminRole))
                .build();
        adminPrincipal = new UserPrincipal(adminUser);

        // Create a BUYER user (target for proxy)
        Role buyerRole = new Role();
        buyerRole.setId(2L);
        buyerRole.setName(RoleName.BUYER);

        User buyerUser = User.builder()
                .id(2L)
                .firstName("Buyer")
                .lastName("User")
                .email("buyer@smartprocure.com")
                .password("encoded-password")
                .status(User.UserStatus.ACTIVE)
                .roles(Set.of(buyerRole))
                .build();
        buyerPrincipal = new UserPrincipal(buyerUser);
    }

    @Nested
    @DisplayName("Normal Token Generation")
    class NormalTokenTests {

        @Test
        @DisplayName("generateToken produces a valid token with correct subject")
        void generateToken_producesValidToken() {
            String token = jwtService.generateToken(buyerPrincipal);

            assertThat(token).isNotNull().isNotEmpty();
            assertThat(jwtService.extractUsername(token)).isEqualTo("buyer@smartprocure.com");
            assertThat(jwtService.extractUserId(token)).isEqualTo(2L);
            assertThat(jwtService.isTokenValid(token, buyerPrincipal)).isTrue();
        }

        @Test
        @DisplayName("generateToken does NOT produce a proxy token")
        void generateToken_isNotProxy() {
            String token = jwtService.generateToken(buyerPrincipal);

            assertThat(jwtService.isProxyToken(token)).isFalse();
        }

        @Test
        @DisplayName("extractAdminId returns null for normal tokens")
        void normalToken_extractAdminId_returnsNull() {
            String token = jwtService.generateToken(buyerPrincipal);

            assertThat(jwtService.extractAdminId(token)).isNull();
        }

        @Test
        @DisplayName("extractTargetUserId returns null for normal tokens")
        void normalToken_extractTargetUserId_returnsNull() {
            String token = jwtService.generateToken(buyerPrincipal);

            assertThat(jwtService.extractTargetUserId(token)).isNull();
        }
    }

    @Nested
    @DisplayName("Proxy Token Generation")
    class ProxyTokenTests {

        @Test
        @DisplayName("generateProxyToken produces a valid token with target user's email as subject")
        void generateProxyToken_hasTargetUserSubject() {
            String proxyToken = jwtService.generateProxyToken(buyerPrincipal, adminPrincipal.getId());

            assertThat(proxyToken).isNotNull().isNotEmpty();
            assertThat(jwtService.extractUsername(proxyToken)).isEqualTo("buyer@smartprocure.com");
        }

        @Test
        @DisplayName("generateProxyToken produces a token marked as proxy")
        void generateProxyToken_isProxyTrue() {
            String proxyToken = jwtService.generateProxyToken(buyerPrincipal, adminPrincipal.getId());

            assertThat(jwtService.isProxyToken(proxyToken)).isTrue();
        }

        @Test
        @DisplayName("generateProxyToken carries adminId claim")
        void generateProxyToken_hasAdminId() {
            String proxyToken = jwtService.generateProxyToken(buyerPrincipal, adminPrincipal.getId());

            assertThat(jwtService.extractAdminId(proxyToken)).isEqualTo(1L);
        }

        @Test
        @DisplayName("generateProxyToken carries targetUserId claim")
        void generateProxyToken_hasTargetUserId() {
            String proxyToken = jwtService.generateProxyToken(buyerPrincipal, adminPrincipal.getId());

            assertThat(jwtService.extractTargetUserId(proxyToken)).isEqualTo(2L);
        }

        @Test
        @DisplayName("generateProxyToken is valid against target user principal")
        void generateProxyToken_validAgainstTargetUser() {
            String proxyToken = jwtService.generateProxyToken(buyerPrincipal, adminPrincipal.getId());

            assertThat(jwtService.isTokenValid(proxyToken, buyerPrincipal)).isTrue();
        }

        @Test
        @DisplayName("generateProxyToken is NOT valid against admin user principal")
        void generateProxyToken_invalidAgainstAdminUser() {
            String proxyToken = jwtService.generateProxyToken(buyerPrincipal, adminPrincipal.getId());

            // The proxy token subject is buyer's email, not admin's
            assertThat(jwtService.isTokenValid(proxyToken, adminPrincipal)).isFalse();
        }

        @Test
        @DisplayName("generateProxyToken has target user's ID in the id claim")
        void generateProxyToken_hasTargetUserIdClaim() {
            String proxyToken = jwtService.generateProxyToken(buyerPrincipal, adminPrincipal.getId());

            assertThat(jwtService.extractUserId(proxyToken)).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("Proxy Token Expiry Independence")
    class ProxyTokenExpiryTests {

        @Test
        @DisplayName("proxy token is still valid immediately after generation")
        void proxyToken_validImmediatelyAfterGeneration() {
            String proxyToken = jwtService.generateProxyToken(buyerPrincipal, adminPrincipal.getId());

            assertThat(jwtService.isTokenValid(proxyToken, buyerPrincipal)).isTrue();
        }

        @Test
        @DisplayName("normal and proxy tokens can coexist independently")
        void normalAndProxyTokens_coexistIndependently() {
            String normalToken = jwtService.generateToken(adminPrincipal);
            String proxyToken = jwtService.generateProxyToken(buyerPrincipal, adminPrincipal.getId());

            // Both are valid
            assertThat(jwtService.isTokenValid(normalToken, adminPrincipal)).isTrue();
            assertThat(jwtService.isTokenValid(proxyToken, buyerPrincipal)).isTrue();

            // Only proxy token has proxy claims
            assertThat(jwtService.isProxyToken(normalToken)).isFalse();
            assertThat(jwtService.isProxyToken(proxyToken)).isTrue();
        }
    }

    @Nested
    @DisplayName("Token Validation Edge Cases")
    class ValidationEdgeCases {

        @Test
        @DisplayName("isTokenValid returns false for tampered tokens")
        void tamperedToken_isInvalid() {
            String token = jwtService.generateToken(buyerPrincipal);
            String tampered = token.substring(0, token.length() - 2) + "xx";

            assertThat(jwtService.isTokenValid(tampered, buyerPrincipal)).isFalse();
        }

        @Test
        @DisplayName("isProxyToken returns false for invalid/malformed tokens")
        void isProxyToken_returnsFalseForInvalidToken() {
            assertThat(jwtService.isProxyToken("not-a-valid-token")).isFalse();
        }
    }
}
