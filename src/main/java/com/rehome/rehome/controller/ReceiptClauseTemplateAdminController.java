package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.ReceiptClauseTemplateRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.ReceiptClauseTemplateResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.ReceiptClauseTemplateAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/receipt-clause-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Receipt Clause Templates")
public class ReceiptClauseTemplateAdminController {

    private final ReceiptClauseTemplateAdminService receiptClauseTemplateAdminService;

    @GetMapping
    @Operation(summary = "Danh sách điều khoản dạng đoạn văn, sắp theo thứ tự hiển thị")
    public ResponseEntity<ApiResponse<List<ReceiptClauseTemplateResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(receiptClauseTemplateAdminService.list(), "OK"));
    }

    @PostMapping
    @Operation(summary = "Thêm điều khoản mới. Không truyền displayOrder thì tự thêm vào cuối")
    public ResponseEntity<ApiResponse<ReceiptClauseTemplateResponse>> create(
            @AuthenticationPrincipal Account admin,
            @RequestBody ReceiptClauseTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                receiptClauseTemplateAdminService.create(admin, request), 201, "Đã thêm điều khoản"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa điều khoản. Chỉ áp dụng cho biên nhận tạo mới sau đó")
    public ResponseEntity<ApiResponse<ReceiptClauseTemplateResponse>> update(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID id,
            @RequestBody ReceiptClauseTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                receiptClauseTemplateAdminService.update(admin, id, request), "Đã cập nhật điều khoản"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xoá điều khoản. Biên nhận đã tạo trước đó không bị ảnh hưởng")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID id) {
        receiptClauseTemplateAdminService.delete(admin, id);
        return ResponseEntity.ok(ApiResponse.success(null, 200, "Đã xoá điều khoản"));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Bật/tắt điều khoản")
    public ResponseEntity<ApiResponse<ReceiptClauseTemplateResponse>> toggle(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                receiptClauseTemplateAdminService.toggle(admin, id), "Đã đổi trạng thái điều khoản"));
    }
}
