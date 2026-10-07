package com.smartprocure.service.impl;

import com.smartprocure.audit.AuditLogService;
import com.smartprocure.dto.response.ProxyLoginResponse;
import com.smartprocure.entity.AuditLog.AuditAction;
import com.smartprocure.entity.ProxySession;
import com.smartprocure.entity.ProxySession.ProxySessionStatus;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.entity.User;
import com.smartprocure.exception.BadRequestException;
import com.smartprocure.exception.ResourceNotFoundException;
import com.smartprocure.repository.ProxySessionRepository;
import com.smartprocure.repository.UserRepository;
import com.smartprocure.security.jwt.JwtService;
import com.smartprocure.security.user.UserPrincipal;
import com.smartprocure.service.ProxyLoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of ProxyLoginService for admin impersonation sessions.
 *
 * Key behaviors:
 * - Only ADMIN users can initiate proxy sessions
 * - Target user must exist, be active, and not have ADMIN role
 * - Nested proxy sessions are rejected (admin already in an active session)
 * - Sessions are time-limited to 60 minutes and auto-expired by a scheduled task
 * - All proxy actions are audit-logged with both admin and target identities
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProxyLoginServiceImpl implements ProxyLoginService {

    private static final long PROXY_SESSION_DURATION_MINUTES = 60;

    private final ProxySessionRepository proxySessionRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public ProxyLoginResponse initiateProxyLogin(Long adminId, Long targetUserId) {
        // 1. Load admin user
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", adminId));

        // 2. Validate target user exists
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> {
                    auditLogService.log(admin, AuditAction.PROXY_LOGIN_DENIED,
                            "USER", targetUserId,
                            "Proxy login denied: target user does not exist (id=" + targetUserId + ")");
                    return new BadRequestException("Target user is not available for proxy login");
                });

        // 3. Validate target user is active
        if (targetUser.getStatus() != User.UserStatus.ACTIVE) {
            auditLogService.log(admin, AuditAction.PROXY_LOGIN_DENIED,
                    "USER", targetUserId,
                    "Proxy login denied: target user is inactive (id=" + targetUserId + ")");
            throw new BadRequestException("Target user is not available for proxy login");
        }

        // 4. Validate target user is not an ADMIN
        boolean targetIsAdmin = targetUser.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.ADMIN);
        if (targetIsAdmin) {
            auditLogService.log(admin, AuditAction.PROXY_LOGIN_DENIED,
                    "USER", targetUserId,
                    "Proxy login denied: target user has ADMIN role (id=" + targetUserId + ")");
            throw new BadRequestException("Target user is not available for proxy login");
        }

        // 5. If an active proxy session exists for this admin, end it first
        proxySessionRepository.findByAdminIdAndStatus(adminId, ProxySessionStatus.ACTIVE)
                .ifPresent(existingSession -> {
                    existingSession.setStatus(ProxySessionStatus.ENDED);
                    existingSession.setEndedAt(LocalDateTime.now());
                    proxySessionRepository.save(existingSession);
                    log.info("Ended existing proxy session (id={}) before starting new one", existingSession.getId());
                });

        // 6. Generate proxy JWT token with proxy claims
        Set<String> targetRoles = targetUser.getRoles().stream()
                .map(role -> "ROLE_" + role.getName().name())
                .collect(Collectors.toSet());

        UserPrincipal targetPrincipal = new UserPrincipal(targetUser);
        String proxyToken = jwtService.generateProxyToken(targetPrincipal, adminId);

        // 7. Create proxy session record
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(PROXY_SESSION_DURATION_MINUTES);

        ProxySession session = ProxySession.builder()
                .admin(admin)
                .targetUser(targetUser)
                .token(proxyToken)
                .startedAt(now)
                .expiresAt(expiresAt)
                .status(ProxySessionStatus.ACTIVE)
                .build();

        proxySessionRepository.save(session);

        // 8. Log PROXY_LOGIN_START
        auditLogService.log(admin, AuditAction.PROXY_LOGIN_START,
                "PROXY_SESSION", session.getId(),
                "Admin " + admin.getEmail() + " started proxy session as " + targetUser.getEmail());

        log.info("Proxy session started: admin={} impersonating={}", admin.getEmail(), targetUser.getEmail());

        // 9. Build and return response
        return ProxyLoginResponse.builder()
                .proxyToken(proxyToken)
                .tokenType("Bearer")
                .targetUserId(targetUser.getId())
                .targetEmail(targetUser.getEmail())
                .targetFirstName(targetUser.getFirstName())
                .targetLastName(targetUser.getLastName())
                .targetRoles(targetRoles)
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    @Transactional
    public void endProxySession(Long adminId) {
        ProxySession session = proxySessionRepository.findByAdminIdAndStatus(adminId, ProxySessionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active proxy session found for this user"));

        // Mark session as ENDED
        session.setStatus(ProxySessionStatus.ENDED);
        session.setEndedAt(LocalDateTime.now());
        proxySessionRepository.save(session);

        // Log PROXY_LOGIN_END
        User admin = session.getAdmin();
        User targetUser = session.getTargetUser();
        auditLogService.log(admin, AuditAction.PROXY_LOGIN_END,
                "PROXY_SESSION", session.getId(),
                "Admin " + admin.getEmail() + " ended proxy session as " + targetUser.getEmail());

        log.info("Proxy session ended: admin={} was impersonating={}", admin.getEmail(), targetUser.getEmail());
    }

    @Override
    public boolean isProxySession(String token) {
        return jwtService.isProxyToken(token);
    }

    /**
     * Scheduled task that runs every minute to expire proxy sessions that have exceeded
     * the 60-minute time limit. Marks them as EXPIRED and logs the event.
     */
    @Scheduled(fixedRate = 60000) // every 60 seconds
    @Transactional
    public void expireOverdueSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<ProxySession> expiredSessions = proxySessionRepository
                .findByStatusAndExpiresAtBefore(ProxySessionStatus.ACTIVE, now);

        for (ProxySession session : expiredSessions) {
            session.setStatus(ProxySessionStatus.EXPIRED);
            session.setEndedAt(now);
            proxySessionRepository.save(session);

            User admin = session.getAdmin();
            User targetUser = session.getTargetUser();
            auditLogService.log(admin, AuditAction.PROXY_LOGIN_EXPIRED,
                    "PROXY_SESSION", session.getId(),
                    "Proxy session expired: admin " + admin.getEmail()
                            + " was impersonating " + targetUser.getEmail());

            log.info("Proxy session expired: admin={} was impersonating={}",
                    admin.getEmail(), targetUser.getEmail());
        }

        if (!expiredSessions.isEmpty()) {
            log.info("Expired {} overdue proxy session(s)", expiredSessions.size());
        }
    }
}
