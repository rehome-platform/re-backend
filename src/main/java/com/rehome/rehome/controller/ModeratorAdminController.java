package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.ChangeModeratorWarehouseRequest;
import com.rehome.rehome.dto.request.CreateModeratorRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.ModeratorAdminResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.ModeratorAdminService;
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
@RequestMapping("/api/admin/moderators")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Moderators")
public class ModeratorAdminController {

    private final ModeratorAdminService moderatorAdminService;

    @PostMapping
    @Operation(summary = "API 138 - Tạo tài khoản Moderator (mã nhân viên tự sinh, mật khẩu tạm gửi qua email)")
    public ResponseEntity<ApiResponse<ModeratorAdminResponse>> create(
            @AuthenticationPrincipal Account admin,
            @Valid @RequestBody CreateModeratorRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                moderatorAdminService.create(admin, request), 201, "Đã tạo tài khoản Moderator"));
    }

    @GetMapping
    @Operation(summary = "API 139 - Danh sách Moderator, lọc theo kho")
    public ResponseEntity<ApiResponse<PageResponse<ModeratorAdminResponse>>> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(moderatorAdminService.list(warehouseId, page, size)));
    }

    @PatchMapping("/{moderatorId}/warehouse")
    @Operation(summary = "API 140 - Chuyển Moderator sang kho khác")
    public ResponseEntity<ApiResponse<ModeratorAdminResponse>> changeWarehouse(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID moderatorId,
            @Valid @RequestBody ChangeModeratorWarehouseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                moderatorAdminService.changeWarehouse(admin, moderatorId, request), "Đã chuyển kho cho Moderator"));
    }
}
