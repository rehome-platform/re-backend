package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.RegisterRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.OtpResponse;
import com.rehome.rehome.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.rehome.rehome.service.OtpRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Registration - Member")
public class MemberProfileController {

    private final AuthService authService;
    private final OtpRateLimiter otpRateLimiter;

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản member")
    public ResponseEntity<ApiResponse<OtpResponse>> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        otpRateLimiter.check(request.getEmail(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(201).body(ApiResponse.success(authService.registerMember(request), 201));
    }
}