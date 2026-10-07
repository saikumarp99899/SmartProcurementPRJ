package com.smartprocure.mapper;

import com.smartprocure.dto.response.UserResponse;
import com.smartprocure.entity.Role;
import com.smartprocure.entity.User;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts User entity → UserResponse DTO.
 * Keeps mapping logic in one place rather than scattering it across services.
 * Service layer calls mapper, controller receives the DTO.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) return null;

        Set<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet());

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .status(user.getStatus().name())
                .roles(roleNames)
                .managerId(user.getManager() != null ? user.getManager().getId() : null)
                .managerName(user.getManager() != null
                        ? user.getManager().getFirstName() + " " + user.getManager().getLastName()
                        : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
