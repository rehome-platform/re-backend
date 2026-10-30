package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.BanAccountRequest;
import com.rehome.rehome.dto.response.AccountStatusResponse;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.AccountAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// /api/admin/** đã được SecurityConfig giới hạn cho ADMIN; @PreAuthorize là lớp bảo vệ thứ hai
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Accounts")
public class AccountAdminController {

    private final AccountAdminService accountAdminService;

    @PostMapping("/{accountId}/ban")
    @Operation(summary = "Khoá tài khoản kèm lý do")
    public ResponseEntity<ApiResponse<AccountStatusResponse>> ban(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID accountId,
            @Valid @RequestBody BanAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                accountAdminService.ban(admin, accountId, request.getReason()), "Đã khoá tài khoản"));
    }

    @PostMapping("/{accountId}/unban")
    @Operation(summary = "Mở khoá tài khoản")
    public ResponseEntity<ApiResponse<AccountStatusResponse>> unban(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(
                accountAdminService.unban(admin, accountId), "Đã mở khoá tài khoản"));
    }
}
