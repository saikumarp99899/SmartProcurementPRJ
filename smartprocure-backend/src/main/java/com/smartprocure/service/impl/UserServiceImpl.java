package com.smartprocure.service.impl;

import com.smartprocure.audit.AuditLogService;
import com.smartprocure.dto.request.UpdateUserRequest;
import com.smartprocure.dto.response.UserResponse;
import com.smartprocure.entity.AuditLog.AuditAction;
import com.smartprocure.entity.Role;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.entity.User;
import com.smartprocure.entity.User.UserStatus;
import com.smartprocure.exception.BadRequestException;
import com.smartprocure.exception.ResourceNotFoundException;
import com.smartprocure.mapper.UserMapper;
import com.smartprocure.repository.RoleRepository;
import com.smartprocure.repository.UserRepository;
import com.smartprocure.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        log.info("Fetching user by id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found with id: {}", id);
                    return new ResourceNotFoundException("User", "id", id);
                });
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        log.info("Fetching all users [page={}, size={}]", pageable.getPageNumber(), pageable.getPageSize());
        Page<UserResponse> result = userRepository.findAll(pageable)
                .map(userMapper::toResponse);
        log.info("Found {} total users", result.getTotalElements());
        return result;
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        log.info("Updating user id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found for update: id={}", id);
                    return new ResourceNotFoundException("User", "id", id);
                });

        if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null && !request.getLastName().isBlank()) {
            user.setLastName(request.getLastName());
        }
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                RoleName roleName = RoleName.valueOf(request.getRole().toUpperCase());
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName));
                user.getRoles().clear();
                user.getRoles().add(role);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid role: " + request.getRole());
            }
        }

        if (request.getManagerId() != null) {
            applyManager(user, request.getManagerId());
        }

        User updated = userRepository.save(user);
        log.info("User updated: {} (id={})", updated.getEmail(), id);
        auditLogService.log(updated, AuditAction.USER_UPDATED, "USER", updated.getId(),
                "User updated: " + updated.getEmail());

        return userMapper.toResponse(updated);
    }

    /**
     * Sets or clears the reporting manager.
     *
     * A managerId of 0 clears it, since a null field means "leave unchanged".
     * Self-reference is rejected because a user cannot approve their own
     * requisition, which would make a REQUESTER_MANAGER step unactionable.
     */
    private void applyManager(User user, Long managerId) {
        if (managerId == 0L) {
            log.info("Clearing manager for user id: {}", user.getId());
            user.setManager(null);
            return;
        }

        if (managerId.equals(user.getId())) {
            log.error("User {} cannot be their own manager", user.getEmail());
            throw new BadRequestException("A user cannot be their own manager.");
        }

        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> {
                    log.error("Manager not found: id={}", managerId);
                    return new ResourceNotFoundException("User", "id", managerId);
                });

        // A cycle would make the reporting line non-terminating.
        for (User ancestor = manager.getManager(); ancestor != null; ancestor = ancestor.getManager()) {
            if (ancestor.getId().equals(user.getId())) {
                log.error("Assigning {} as manager of {} would create a reporting cycle",
                        manager.getEmail(), user.getEmail());
                throw new BadRequestException(
                        "That assignment would create a circular reporting line.");
            }
        }

        log.info("Setting manager of user id {} to {}", user.getId(), manager.getEmail());
        user.setManager(manager);
    }

    @Override
    @Transactional
    public UserResponse activateUser(Long id) {
        log.info("Activating user id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found for activation: id={}", id);
                    return new ResourceNotFoundException("User", "id", id);
                });
        user.setStatus(UserStatus.ACTIVE);
        User updated = userRepository.save(user);
        log.info("User activated: {} (id={})", updated.getEmail(), id);
        auditLogService.log(updated, AuditAction.USER_ACTIVATED, "USER", updated.getId(),
                "User activated: " + updated.getEmail());
        return userMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public UserResponse deactivateUser(Long id) {
        log.info("Deactivating user id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found for deactivation: id={}", id);
                    return new ResourceNotFoundException("User", "id", id);
                });
        user.setStatus(UserStatus.INACTIVE);
        User updated = userRepository.save(user);
        log.info("User deactivated: {} (id={})", updated.getEmail(), id);
        auditLogService.log(updated, AuditAction.USER_DEACTIVATED, "USER", updated.getId(),
                "User deactivated: " + updated.getEmail());
        return userMapper.toResponse(updated);
    }
}
