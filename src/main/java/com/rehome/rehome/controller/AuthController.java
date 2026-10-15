package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.AuthResponse;
import com.rehome.rehome.dto.request.LoginRequest;
import com.rehome.rehome.dto.request.RefreshTokenRequest;
import com.rehome.rehome.dto.request.RegisterRequest;
import com.rehome.rehome.dto.response.OtpResponse;
import com.rehome.rehome.dto.request.OtpRequest;
import com.rehome.rehome.dto.request.VerifyOtpRequest;
import com.rehome.rehome.dto.request.ResetPasswordRequest;
import com.rehome.rehome.service.OtpService;
import com.rehome.rehome.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    @PostMapping("/register")
    public ResponseEntity<OtpResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<OtpResponse> resendOtp(
            @Valid @RequestBody OtpRequest request) {
        return ResponseEntity.ok(otpService.resend(request));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<OtpResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(otpService.verify(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<OtpResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(otpService.resetPassword(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        authService.logout(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
