package com.smartprocure.repository;

import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.entity.User;
import com.smartprocure.entity.User.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

/**
 * Spring Data JPA repository for User entity.
 * JpaRepository provides: save, findById, findAll, delete, count, exists — all out of the box.
 * We add custom query methods using Spring's method-name convention.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByStatus(UserStatus status);

    /**
     * Finds active users holding a given role.
     * Used to show requesters who is expected to approve their requisition.
     */
    List<User> findByRoles_NameAndStatus(RoleName roleName, UserStatus status);
}
