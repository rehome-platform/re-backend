package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateViolationWordRequest;
import com.rehome.rehome.dto.request.UpdateViolationWordRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ViolationWordResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ViolationWordCategory;
import com.rehome.rehome.entity.enums.ViolationWordSeverity;
import com.rehome.rehome.service.ViolationWordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// /api/admin/** đã được SecurityConfig giới hạn cho ADMIN; @PreAuthorize là lớp bảo vệ thứ hai
@RestController
@RequestMapping("/api/admin/violation-words")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Violation Words")
public class ViolationWordAdminController {

    private final ViolationWordService violationWordService;

    @GetMapping
    @Operation(summary = "Tìm từ khóa vi phạm (theo từ, nhóm, mức độ, trạng thái hoạt động)")
    public ResponseEntity<ApiResponse<PageResponse<ViolationWordResponse>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ViolationWordCategory category,
            @RequestParam(required = false) ViolationWordSeverity severity,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                violationWordService.search(keyword, category, severity, isActive, page, size)));
    }

    @PostMapping
    @Operation(summary = "Thêm từ khóa vi phạm")
    public ResponseEntity<ApiResponse<ViolationWordResponse>> create(
            @AuthenticationPrincipal Account admin,
            @Valid @RequestBody CreateViolationWordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                violationWordService.create(admin, request), 201, "Đã thêm từ khóa vi phạm"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật từ khóa vi phạm")
    public ResponseEntity<ApiResponse<ViolationWordResponse>> update(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateViolationWordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                violationWordService.update(admin, id, request), "Đã cập nhật từ khóa vi phạm"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa từ khóa vi phạm")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID id) {
        violationWordService.delete(admin, id);
        return ResponseEntity.ok(ApiResponse.success(null, 200, "Đã xóa từ khóa vi phạm"));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Bật/tắt trạng thái hoạt động của từ khóa")
    public ResponseEntity<ApiResponse<ViolationWordResponse>> toggle(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                violationWordService.toggle(admin, id), "Đã đổi trạng thái từ khóa"));
    }
}
