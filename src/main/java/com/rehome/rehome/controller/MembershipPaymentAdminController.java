package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.AdminMembershipPaymentResponse;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.service.MembershipPaymentAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/membership-payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Membership")
public class MembershipPaymentAdminController {

    private final MembershipPaymentAdminService membershipPaymentAdminService;

    @GetMapping
    @Operation(summary = "Danh sách giao dịch mua gói, mới nhất trước. from/to là yyyy-MM-dd theo giờ VN (from <= to), "
            + "tier = SILVER/GOLD/DIAMOND, method = WALLET/MOMO/VNPAY")
    public ResponseEntity<ApiResponse<PageResponse<AdminMembershipPaymentResponse>>> search(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                membershipPaymentAdminService.search(from, to, tier, method, page, size), "OK"));
    }
}
