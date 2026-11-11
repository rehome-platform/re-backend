package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.AccountGrowthResponse;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.service.AccountGrowthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Tách khỏi AccountAdminController (/api/admin/accounts) cho gọn; hai controller không trùng mapping.
// /api/admin/** đã được SecurityConfig giới hạn cho ADMIN; @PreAuthorize là lớp bảo vệ thứ hai
@RestController
@RequestMapping("/api/admin/accounts/growth")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Accounts")
public class AccountGrowthAdminController {

    private final AccountGrowthService accountGrowthService;

    @GetMapping
    @Operation(summary = "Số tài khoản đăng ký theo ngày/tuần/tháng (granularity=day|week|month), from/to yyyy-MM-dd, mặc định 30 ngày gần nhất")
    public ResponseEntity<ApiResponse<AccountGrowthResponse>> growth(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) Role role) {
        return ResponseEntity.ok(ApiResponse.success(accountGrowthService.growth(from, to, granularity, role)));
    }
}
