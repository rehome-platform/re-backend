package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.RegisterRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.OrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import com.rehome.rehome.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationProfileController {
    private final OrganizationService organizationService;
    private final AuthService authService;

    @PostMapping("/register/organization")
    @Operation(summary = "Đăng ký tài khoản tổ chức")
    public ResponseEntity<ApiResponse<RegisterOrganizationResponse>> registerOrganization(
            @Valid @RequestBody RegisterOrganizationRequest request,
            @AuthenticationPrincipal Account account) {
        return ResponseEntity.status(201)
                .body(ApiResponse.success(organizationService.registerOrganization(account.getId(), request), 201));
    }

    @PutMapping("/me/registration")   // class có @RequestMapping("/organizations")
    @Operation(summary = "Tổ chức nộp lại hồ sơ đăng ký sau khi bị từ chối")
    public ResponseEntity<ApiResponse<OrganizationResubmitResponse>> resubmit(
            @AuthenticationPrincipal Account account,
            @Valid @RequestBody RegisterOrganizationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                organizationService.resubmit(account, request),
                "Đã nộp lại hồ sơ, chờ duyệt"));
    }

    @PostMapping("/register/account")
    @Operation(summary = "Đăng ký tài khoản organization")
    public ResponseEntity<ApiResponse<OtpResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(authService.registerOrganization(request), 201));
    }

    @GetMapping("/options")
    @Operation(summary = "Danh sách loại tổ chức và phạm vi hoạt động cho form đăng ký")
    public ResponseEntity<ApiResponse<OrganizationOptionsResponse>> getOptions() {
        return ResponseEntity.ok(ApiResponse.success(organizationService.getOptions()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ORGANIZATION')")
    public ResponseEntity<OrganizationProfileResponse> getMyProfile(
            @AuthenticationPrincipal Account account
    ) {
        return ResponseEntity.ok(organizationService.getMyProfile(account.getId()));
    }
}
