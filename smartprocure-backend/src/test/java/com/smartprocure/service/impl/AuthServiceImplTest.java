package com.smartprocure.service.impl;

import com.smartprocure.audit.AuditLogService;
import com.smartprocure.dto.request.LoginRequest;
import com.smartprocure.dto.request.RegisterRequest;
import com.smartprocure.dto.response.LoginResponse;
import com.smartprocure.dto.response.UserResponse;
import com.smartprocure.entity.Role;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.entity.User;
import com.smartprocure.exception.BadRequestException;
import com.smartprocure.exception.DuplicateResourceException;
import com.smartprocure.exception.ResourceNotFoundException;
import com.smartprocure.mapper.UserMapper;
import com.smartprocure.repository.RoleRepository;
import com.smartprocure.repository.UserRepository;
import com.smartprocure.security.jwt.JwtService;
import com.smartprocure.security.user.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthServiceImpl.
 *
 * Uses Mockito to isolate the service from its dependencies.
 * Each test verifies ONE behavior — making failures easy to diagnose.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private UserMapper userMapper;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private AuthServiceImpl authService;

    private Role buyerRole;
    private Role adminRole;
    private User savedUser;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        buyerRole = new Role();
        buyerRole.setId(1L);
        buyerRole.setName(RoleName.BUYER);

        adminRole = new Role();
        adminRole.setId(2L);
        adminRole.setName(RoleName.ADMIN);

        savedUser = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("encoded_password")
                .status(User.UserStatus.ACTIVE)
                .build();
        savedUser.getRoles().add(buyerRole);

        userResponse = UserResponse.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .status("ACTIVE")
                .roles(Set.of("BUYER"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    // =========================================================================
    // REGISTER TESTS
    // =========================================================================
    @Nested
    @DisplayName("Register")
    class RegisterTests {

        private RegisterRequest createRegisterRequest() {
            RegisterRequest request = new RegisterRequest();
            request.setFirstName("John");
            request.setLastName("Doe");
            request.setEmail("john@example.com");
            request.setPassword("password123");
            request.setRole("BUYER");
            return request;
        }

        @Test
        @DisplayName("Should register user successfully with valid data")
        void register_Success() {
            // Arrange
            RegisterRequest request = createRegisterRequest();

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.BUYER)).thenReturn(Optional.of(buyerRole));
            when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

            // Act
            UserResponse result = authService.register(request);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("john@example.com");
            assertThat(result.getFirstName()).isEqualTo("John");
            assertThat(result.getRoles()).contains("BUYER");

            // Verify interactions
            verify(userRepository).existsByEmail("john@example.com");
            verify(passwordEncoder).encode("password123");
            verify(userRepository).save(any(User.class));
            verify(auditLogService).log(any(), any(), anyString(), any(), anyString());
        }

        @Test
        @DisplayName("Should default to BUYER role when no role specified")
        void register_DefaultsToBuyerRole() {
            // Arrange
            RegisterRequest request = createRegisterRequest();
            request.setRole(null); // No role specified

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.BUYER)).thenReturn(Optional.of(buyerRole));
            when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

            // Act
            authService.register(request);

            // Assert — verify it looked up BUYER role
            verify(roleRepository).findByName(RoleName.BUYER);
        }

        @Test
        @DisplayName("Should throw DuplicateResourceException for existing email")
        void register_DuplicateEmail_ThrowsException() {
            // Arrange
            RegisterRequest request = createRegisterRequest();
            when(userRepository.existsByEmail(anyString())).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already registered");

            // Verify save was never called
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BadRequestException for invalid role")
        void register_InvalidRole_ThrowsException() {
            // Arrange
            RegisterRequest request = createRegisterRequest();
            request.setRole("INVALID_ROLE");
            when(userRepository.existsByEmail(anyString())).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid role");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when role not in DB")
        void register_RoleNotInDatabase_ThrowsException() {
            // Arrange
            RegisterRequest request = createRegisterRequest();
            request.setRole("ADMIN");
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Role not found");
        }

        @Test
        @DisplayName("Should store email in lowercase")
        void register_EmailStoredInLowercase() {
            // Arrange
            RegisterRequest request = createRegisterRequest();
            request.setEmail("JOHN@EXAMPLE.COM");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.BUYER)).thenReturn(Optional.of(buyerRole));
            when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

            // Act
            authService.register(request);

            // Assert — capture the saved user and verify email is lowercase
            verify(userRepository).save(argThat(user ->
                    user.getEmail().equals("john@example.com")));
        }
    }

    // =========================================================================
    // LOGIN TESTS
    // =========================================================================
    @Nested
    @DisplayName("Login")
    class LoginTests {

        private LoginRequest createLoginRequest() {
            LoginRequest request = new LoginRequest();
            request.setEmail("john@example.com");
            request.setPassword("password123");
            return request;
        }

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void login_Success() {
            // Arrange
            LoginRequest request = createLoginRequest();

            UserPrincipal userPrincipal = new UserPrincipal(savedUser);
            Authentication authentication = mock(Authentication.class);

            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userPrincipal);
            when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt-token-123");
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(savedUser));

            // Act
            LoginResponse result = authService.login(request);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getAccessToken()).isEqualTo("jwt-token-123");
            assertThat(result.getTokenType()).isEqualTo("Bearer");
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getEmail()).isEqualTo("john@example.com");
            assertThat(result.getFirstName()).isEqualTo("John");
            assertThat(result.getRoles()).contains("ROLE_BUYER");
        }

        @Test
        @DisplayName("Should throw exception for invalid credentials")
        void login_InvalidCredentials_ThrowsException() {
            // Arrange
            LoginRequest request = createLoginRequest();
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Invalid credentials"));

            // Act & Assert
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("Should convert email to lowercase before authenticating")
        void login_EmailConvertedToLowercase() {
            // Arrange
            LoginRequest request = createLoginRequest();
            request.setEmail("JOHN@EXAMPLE.COM");

            UserPrincipal userPrincipal = new UserPrincipal(savedUser);
            Authentication authentication = mock(Authentication.class);

            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userPrincipal);
            when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt-token");
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(savedUser));

            // Act
            authService.login(request);

            // Assert — verify authentication was called with lowercase email
            verify(authenticationManager).authenticate(
                    argThat(token -> ((UsernamePasswordAuthenticationToken) token)
                            .getPrincipal().equals("john@example.com")));
        }
    }

    // =========================================================================
    // GET CURRENT USER TESTS
    // =========================================================================
    @Nested
    @DisplayName("GetCurrentUser")
    class GetCurrentUserTests {

        @Test
        @DisplayName("Should return user response for valid email")
        void getCurrentUser_Success() {
            // Arrange
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser));
            when(userMapper.toResponse(savedUser)).thenReturn(userResponse);

            // Act
            UserResponse result = authService.getCurrentUser("john@example.com");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("john@example.com");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException for unknown email")
        void getCurrentUser_NotFound_ThrowsException() {
            // Arrange
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authService.getCurrentUser("unknown@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
