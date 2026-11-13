package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.VoucherTemplateRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.VoucherTemplateResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.VoucherTemplateAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin - mẫu voucher (màn W-04).
 *
 * <p>Không có DELETE: {@code member_vouchers} trỏ FK vào template để thống kê. Chỉ "Ngừng phát".
 */
@RestController
@RequestMapping("/api/admin/voucher-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Membership")
public class VoucherTemplateAdminController {

    private final VoucherTemplateAdminService voucherTemplateAdminService;

    @GetMapping
    @Operation(summary = "Danh sách mẫu voucher kèm rule theo hạng")
    public ResponseEntity<ApiResponse<List<VoucherTemplateResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(voucherTemplateAdminService.list(), "OK"));
    }

    @PostMapping
    @Operation(summary = "Tạo mẫu. type = ORDER_DISCOUNT thì BẮT BUỘC discountKind = PERCENT "
            + "và phải có maxDiscountAmount")
    public ResponseEntity<ApiResponse<VoucherTemplateResponse>> create(
            @AuthenticationPrincipal Account admin,
            @RequestBody VoucherTemplateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                voucherTemplateAdminService.create(admin, request), 201, "Đã tạo mẫu voucher"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa mẫu + thay thế toàn bộ rule theo hạng. Mã ĐÃ PHÁT không đổi theo "
            + "(MemberVoucher snapshot luật lúc phát)")
    public ResponseEntity<ApiResponse<VoucherTemplateResponse>> update(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID id,
            @RequestBody VoucherTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherTemplateAdminService.update(admin, id, request), "Đã cập nhật mẫu voucher"));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Ngừng phát mã mới. Mã đã phát vẫn dùng được tới khi hết hạn của chính nó")
    public ResponseEntity<ApiResponse<VoucherTemplateResponse>> deactivate(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherTemplateAdminService.deactivate(admin, id), "Đã ngừng phát mẫu voucher"));
    }
}
