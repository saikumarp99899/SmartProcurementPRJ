package com.smartprocure.service;

import com.smartprocure.dto.request.UpdateUserRequest;
import com.smartprocure.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    UserResponse getUserById(Long id);
    Page<UserResponse> getAllUsers(Pageable pageable);
    UserResponse updateUser(Long id, UpdateUserRequest request);
    UserResponse activateUser(Long id);
    UserResponse deactivateUser(Long id);
}
