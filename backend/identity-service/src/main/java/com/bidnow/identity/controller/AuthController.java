package com.bidnow.identity.controller;

import com.bidnow.common.dto.BaseResponse;
import com.bidnow.identity.dto.request.LoginRequest;
import com.bidnow.identity.dto.request.RefreshTokenRequest;
import com.bidnow.identity.dto.request.RegisterRequest;
import com.bidnow.identity.dto.request.ResendOtpRequest;
import com.bidnow.identity.dto.request.VerifyOtpRequest;
import com.bidnow.identity.dto.response.LoginResponse;
import com.bidnow.identity.dto.response.RegisterResponse;
import com.bidnow.identity.dto.response.ResendOtpResponse;
import com.bidnow.identity.dto.response.VerifyOtpResponse;
import com.bidnow.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration, login, and account verification")
public class AuthController {

    private final AuthService authService;

    /**
     * Phase 1 – Issue #36
     * Accepts registration form, generates OTP, saves user as PENDING_VERIFICATION,
     * and emits USER_VERIFICATION_REQUESTED event.
     * Returns 202 Accepted to indicate that email verification is required.
     */
    @Operation(summary = "Register a new user", description = "Creates a new user account in PENDING_VERIFICATION state and sends an OTP email.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Registration accepted, OTP sent",
                    content = @Content(schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or email already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<BaseResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(BaseResponse.<RegisterResponse>builder()
                        .status(HttpStatus.ACCEPTED.value())
                        .message("Registration successful. Please check your email for the OTP to verify your account.")
                        .data(response)
                        .build());
    }

    /**
     * Phase 2 – Issue #37
     * Validates the 6-digit OTP, activates the account, creates the user profile,
     * and emits USER_REGISTERED event (triggers Welcome Email).
     * Returns 200 OK on success.
     */
    @Operation(summary = "Verify OTP", description = "Validates the OTP sent to the user's email and activates the account.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email verified successfully",
                    content = @Content(schema = @Schema(implementation = VerifyOtpResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or expired OTP")
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<BaseResponse<VerifyOtpResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        VerifyOtpResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(BaseResponse.success("Email verified successfully. Your account is now active.", response));
    }

    /**
     * Resend a fresh OTP to the user's email.
     * Only allowed when the account is PENDING_VERIFICATION and the previous OTP has already expired.
     * Also resets the failed-attempt counter so the user gets a clean slate.
     */
    @Operation(summary = "Resend OTP", description = "Sends a new OTP to the user's email if the previous one expired.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "New OTP sent successfully",
                    content = @Content(schema = @Schema(implementation = ResendOtpResponse.class))),
            @ApiResponse(responseCode = "400", description = "Account not in pending state or OTP not yet expired")
    })
    @PostMapping("/resend-otp")
    public ResponseEntity<BaseResponse<ResendOtpResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        ResendOtpResponse response = authService.resendOtp(request);
        return ResponseEntity.ok(BaseResponse.success("A new OTP has been sent to your email.", response));
    }

    @Operation(summary = "User login", description = "Authenticates user and returns access and refresh tokens.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<BaseResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Refresh token", description = "Generates a new access token using a valid refresh token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "User logout", description = "Invalidates the refresh token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Logout successful"),
            @ApiResponse(responseCode = "401", description = "Invalid refresh token")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
