package com.bidnow.identity.service;

import com.bidnow.identity.dto.request.LoginRequest;
import com.bidnow.identity.dto.request.RegisterRequest;
import com.bidnow.identity.dto.response.LoginResponse;
import com.bidnow.identity.dto.response.RegisterResponse;

public interface AuthService {
    RegisterResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    LoginResponse refresh(String refreshToken);
    void logout(String refreshToken);
}
