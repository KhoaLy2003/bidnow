package com.bidnow.identity.service;

import com.bidnow.identity.dto.request.RegisterRequest;
import com.bidnow.identity.dto.response.RegisterResponse;

public interface AuthService {
    RegisterResponse register(RegisterRequest request);
}
