package com.smartprocure.security.user;

import com.smartprocure.entity.User;
import com.smartprocure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security calls loadUserByUsername() during authentication.
 * This implementation loads the user by email from MySQL.
 *
 * The @Transactional is important here because User has EAGER-loaded roles.
 * Without a transaction, Hibernate may throw LazyInitializationException
 * when accessing the roles collection outside a session.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));
        return new UserPrincipal(user);
    }
}
