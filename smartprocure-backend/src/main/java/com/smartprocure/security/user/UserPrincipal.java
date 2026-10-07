package com.smartprocure.security.user;

import com.smartprocure.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Adapter between our User entity and Spring Security's UserDetails interface.
 *
 * Spring Security doesn't know about our User entity.
 * It works with UserDetails objects.
 * UserPrincipal wraps our User and implements UserDetails so Spring Security can use it.
 *
 * At runtime:
 * 1. CustomUserDetailsService.loadUserByUsername() loads User from DB
 * 2. Wraps it in UserPrincipal
 * 3. Spring Security uses UserPrincipal for authentication
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.active = user.getStatus() == User.UserStatus.ACTIVE;

        // Convert our Role enum set to Spring Security GrantedAuthority list
        // Spring Security expects roles prefixed with "ROLE_"
        // e.g., Role.ADMIN → "ROLE_ADMIN"
        this.authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getUsername() {
        return email; // We use email as the username
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
