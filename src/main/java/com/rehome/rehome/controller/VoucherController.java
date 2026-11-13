package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.ApplicableVouchersResponse;
import com.rehome.rehome.dto.response.MemberVoucherResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ví voucher của Member - màn M-04, và bottom-sheet chọn mã ở M-07. */
@RestController
@RequestMapping("/vouchers")
@RequiredArgsConstructor
@Tag(name = "Voucher")
public class VoucherController {

    private final VoucherService voucherService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Ví voucher. status rỗng = tất cả. UNUSED trước, sắp hết hạn lên đầu")
    public ResponseEntity<ApiResponse<PageResponse<MemberVoucherResponse>>> getMyVouchers(
            @AuthenticationPrincipal Account account,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAccount(account);
        return ResponseEntity.ok(ApiResponse.success(
                voucherService.getMyVouchers(account, parseStatus(status), page, size)));
    }

    /**
     * §8.6 - voucher dùng được cho đơn sắp đặt, kèm số tiền giảm THẬT.
     *
     * <p>{@code productIds} là CSV, bỏ trống = lấy cả giỏ hàng (giống {@code POST /orders}).
     * Mobile <b>phải</b> gọi API này để hiện số tiền giảm, không tự tính ở client.
     */
    @GetMapping("/applicable")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Voucher dùng được cho đơn sắp đặt + số tiền giảm thật của từng mã")
    public ResponseEntity<ApiResponse<ApplicableVouchersResponse>> getApplicable(
            @AuthenticationPrincipal Account account,
            @RequestParam(required = false) String productIds,
            @RequestParam UUID shippingAddressId) {
        requireAccount(account);
        return ResponseEntity.ok(ApiResponse.success(voucherService.getApplicableVouchers(
                account, parseCsv(productIds), shippingAddressId)));
    }

    private static MemberVoucherStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return MemberVoucherStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("status không hợp lệ: " + status);
        }
    }

    private static List<String> parseCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            // Bỏ id trùng để một món gửi hai lần không bị tính thành hai đơn khi xem trước
            if (!trimmed.isEmpty() && !ids.contains(trimmed)) {
                ids.add(trimmed);
            }
        }
        return ids;
    }

    private static void requireAccount(Account account) {
        if (account == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }
}
