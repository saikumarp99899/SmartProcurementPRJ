package com.smartprocure.service.impl;

import com.smartprocure.audit.AuditLogService;
import com.smartprocure.dto.request.LoginRequest;
import com.smartprocure.dto.request.RegisterRequest;
import com.smartprocure.dto.response.LoginResponse;
import com.smartprocure.dto.response.UserResponse;
import com.smartprocure.entity.AuditLog.AuditAction;
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
import com.smartprocure.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles user registration, login, and current user retrieval.
 *
 * Registration flow:
 * 1. Validate email is not already taken
 * 2. Look up the requested role (default BUYER)
 * 3. Encode the password with BCrypt
 * 4. Save user to DB
 * 5. Write audit log
 *
 * Login flow:
 * 1. Spring Security's AuthenticationManager authenticates the credentials
 * 2. If valid, generate JWT token
 * 3. Return token + user info
 * 4. Write audit log
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // 1. Check email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                "Email is already registered: " + request.getEmail());
        }

        // 2. Determine role — default to BUYER
        final RoleName roleName;
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                roleName = RoleName.valueOf(request.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid role: " + request.getRole());
            }
        } else {
            roleName = RoleName.BUYER;
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Role not found: " + roleName + ". Please seed the roles table."));

        // 3. Build and save the user
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword())) // BCrypt hash
                .status(User.UserStatus.ACTIVE)
                .build();
        user.getRoles().add(role);

        User savedUser = userRepository.save(user);
        log.info("New user registered: {}", savedUser.getEmail());

        // 4. Audit log (async — does not block the response)
        auditLogService.log(savedUser, AuditAction.USER_CREATED,
                "USER", savedUser.getId(),
                "User registered: " + savedUser.getEmail());

        return userMapper.toResponse(savedUser);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // Delegates to Spring Security's authentication machinery.
        // Internally calls CustomUserDetailsService.loadUserByUsername()
        // then BCryptPasswordEncoder.matches().
        // Throws BadCredentialsException if invalid → caught by GlobalExceptionHandler.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().toLowerCase(),
                        request.getPassword()
                )
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(userPrincipal);

        Set<String> roles = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // Audit log for login
        User user = userRepository.findByEmail(userPrincipal.getEmail()).orElse(null);
        auditLogService.log(user, AuditAction.LOGIN, "USER", userPrincipal.getId(),
                "User logged in: " + userPrincipal.getEmail());

        log.info("User logged in: {}", userPrincipal.getEmail());

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(userPrincipal.getId())
                .email(userPrincipal.getEmail())
                .firstName(user != null ? user.getFirstName() : "")
                .lastName(user != null ? user.getLastName() : "")
                .roles(roles)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return userMapper.toResponse(user);
    }
}
