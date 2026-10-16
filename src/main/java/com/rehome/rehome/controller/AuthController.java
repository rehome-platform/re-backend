package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.*;
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
    public ResponseEntity<OtpResponse> resendOtp(
            @Valid @RequestBody OtpRequest request) {
        return ResponseEntity.ok(otpService.resend(request));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<OtpResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.status(200).body(otpService.verify(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request));
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

    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        otpService.forgotPassword(request);
        return ResponseEntity.noContent().build();   // 204, luôn trả như nhau
    }

    @PostMapping("/password/reset")
    public ResponseEntity<OtpResponse> reset(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(otpService.resetPassword(request));
    }

    @PostMapping("/password/change")
    public ResponseEntity<String> changePassword(
            @AuthenticationPrincipal Account account,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(account, request);
        return ResponseEntity.ok("Đổi mật khẩu thành công");
    }
}
