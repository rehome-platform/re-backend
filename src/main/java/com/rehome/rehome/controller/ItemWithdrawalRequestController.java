package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateItemWithdrawalRequest;
import com.rehome.rehome.dto.request.PayItemWithdrawalFeeRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.ItemWithdrawalResponse;
import com.rehome.rehome.dto.response.PayItemWithdrawalFeeResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.ItemWithdrawalRequestService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/item-withdrawal-requests")
@RequiredArgsConstructor
public class ItemWithdrawalRequestController {

    private final ItemWithdrawalRequestService itemWithdrawalRequestService;

    // API 90 (F-032)
    @PostMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<ItemWithdrawalResponse>> create(
            @AuthenticationPrincipal Account account,
            @RequestBody CreateItemWithdrawalRequest request) {

        ItemWithdrawalResponse result = itemWithdrawalRequestService.create(account, request);

        return ResponseEntity.ok(ApiResponse.success(result, HttpStatus.CREATED.value(), "Đã tạo yêu cầu rút hàng"));
    }

    // API 91 (F-033)
    @PostMapping("/{id}/pay-fee")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PayItemWithdrawalFeeResponse>> payFee(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id,
            @RequestBody PayItemWithdrawalFeeRequest request,
            HttpServletRequest httpRequest) {

        PayItemWithdrawalFeeResponse result = itemWithdrawalRequestService.payFee(
                account, id, request, clientIp(httpRequest));

        // Còn phải trả qua cổng MoMo/VNPay thì chỉ có payUrl, chưa có gì để báo "đã thanh toán"
        String message = StringUtils.hasText(result.getPayUrl()) ? "" : "Đã thanh toán phí rút hàng";

        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
