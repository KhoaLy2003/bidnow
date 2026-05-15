package com.bidnow.identity.service;

import com.bidnow.identity.dto.request.LoginRequest;
import com.bidnow.identity.dto.request.RegisterRequest;
import com.bidnow.identity.dto.request.ResendOtpRequest;
import com.bidnow.identity.dto.request.VerifyOtpRequest;
import com.bidnow.identity.dto.response.LoginResponse;
import com.bidnow.identity.dto.response.RegisterResponse;
import com.bidnow.identity.dto.response.ResendOtpResponse;
import com.bidnow.identity.dto.response.VerifyOtpResponse;

public interface AuthService {
    RegisterResponse register(RegisterRequest request);

    VerifyOtpResponse verifyOtp(VerifyOtpRequest request);

    ResendOtpResponse resendOtp(ResendOtpRequest request);

    LoginResponse login(LoginRequest request);

    LoginResponse refresh(String refreshToken);

    void logout(String refreshToken);
}
