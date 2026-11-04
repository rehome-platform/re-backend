package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateWarehouseRequest;
import com.rehome.rehome.dto.request.UpdateWarehouseRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.WarehouseAdminResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.service.WarehouseAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Tách khỏi WarehouseController (/warehouses, API cho Member/Moderator) để không đụng rule /warehouses/** trong SecurityConfig
@RestController
@RequestMapping("/api/admin/warehouses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Warehouses")
public class WarehouseAdminController {

    private final WarehouseAdminService warehouseAdminService;

    @GetMapping
    @Operation(summary = "API 141 - Danh sách kho (gồm cả kho đã ẩn), lọc theo status")
    public ResponseEntity<ApiResponse<List<WarehouseAdminResponse>>> list(
            @RequestParam(required = false) CatalogStatus status) {
        return ResponseEntity.ok(ApiResponse.success(warehouseAdminService.list(status)));
    }

    @PostMapping
    @Operation(summary = "API 142 - Tạo kho")
    public ResponseEntity<ApiResponse<WarehouseAdminResponse>> create(
            @AuthenticationPrincipal Account admin,
            @Valid @RequestBody CreateWarehouseRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                warehouseAdminService.create(admin, request), 201, "Đã tạo kho"));
    }

    @PutMapping("/{warehouseId}")
    @Operation(summary = "API 143 - Sửa thông tin kho")
    public ResponseEntity<ApiResponse<WarehouseAdminResponse>> update(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID warehouseId,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                warehouseAdminService.update(admin, warehouseId, request), "Đã cập nhật kho"));
    }

    @PostMapping("/{warehouseId}/hide")
    @Operation(summary = "API 144 - Ẩn kho (409 WAREHOUSE_HAS_STOCK nếu kho còn hàng)")
    public ResponseEntity<ApiResponse<WarehouseAdminResponse>> hide(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(
                warehouseAdminService.hide(admin, warehouseId), "Đã ẩn kho"));
    }
}
