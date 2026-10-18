package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.*;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.AuthResponse;
import com.rehome.rehome.dto.response.OtpResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.OtpService;
import com.rehome.rehome.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> resendOtp(
            @Valid @RequestBody OtpRequest request) {
        return ResponseEntity.ok(ApiResponse.success(otpService.resend(request)));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.success(otpService.verify(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.loginWithGoogle(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refreshToken(request.getRefreshToken())));
    }

    // Đổi 204 -> 200 vì 204 không có body, không đặt được ApiResponse
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication) {
        authService.logout(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(null, "Đăng xuất thành công"));
    }

    // Luôn trả như nhau để không lộ email có tồn tại hay không
    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<Void>> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        otpService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<OtpResponse>> reset(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(otpService.resetPassword(request)));
    }

    @PostMapping("/password/change")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal Account account,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(account, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Đổi mật khẩu thành công"));
    }
}