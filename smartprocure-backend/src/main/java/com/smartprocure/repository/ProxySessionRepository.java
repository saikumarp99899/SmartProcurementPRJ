package com.smartprocure.repository;

import com.smartprocure.entity.ProxySession;
import com.smartprocure.entity.ProxySession.ProxySessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProxySessionRepository extends JpaRepository<ProxySession, Long> {

    Optional<ProxySession> findByAdminIdAndStatus(Long adminId, ProxySessionStatus status);

    Optional<ProxySession> findByTokenAndStatus(String token, ProxySessionStatus status);

    List<ProxySession> findByStatusAndExpiresAtBefore(ProxySessionStatus status, LocalDateTime now);
}
