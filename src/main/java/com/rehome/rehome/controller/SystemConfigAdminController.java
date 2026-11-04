package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.UpdateSystemConfigRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.SystemConfigResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.SystemConfigAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Config")
public class SystemConfigAdminController {

    private final SystemConfigAdminService systemConfigAdminService;

    @PutMapping("/{key}")
    @Operation(summary = "API 146 - Sửa một tham số cấu hình (F-157), ghi audit log UPDATE_CONFIG")
    public ResponseEntity<ApiResponse<SystemConfigResponse>> update(
            @AuthenticationPrincipal Account admin,
            @PathVariable String key,
            @RequestBody UpdateSystemConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                systemConfigAdminService.update(admin, key, request.getValue()), "OK"));
    }
}
