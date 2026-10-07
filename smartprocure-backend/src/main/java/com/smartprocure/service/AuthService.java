package com.smartprocure.service;

import com.smartprocure.dto.request.LoginRequest;
import com.smartprocure.dto.request.RegisterRequest;
import com.smartprocure.dto.response.LoginResponse;
import com.smartprocure.dto.response.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    UserResponse getCurrentUser(String email);
}
