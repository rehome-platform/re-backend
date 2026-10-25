package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateGatewayPaymentRequest;
import com.rehome.rehome.dto.request.MomoIpnRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CreateMomoResponse;
import com.rehome.rehome.dto.response.PaymentStatusResponse;
import com.rehome.rehome.service.MomoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/momo")
public class MomoController {

    private final MomoService momoService;

    @PostMapping("create")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<CreateMomoResponse>> createPayment(
            @RequestBody CreateGatewayPaymentRequest request,
            Authentication authentication) {

        String email = authentication.getName();
        CreateMomoResponse response = momoService.createPaymentForOrders(
                email, request.toOrderIds(), request.getRedirectUrl());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("ipn-handler")
    public ResponseEntity<Void> ipnHandler(@RequestBody MomoIpnRequest ipnRequest) {

        momoService.handleIpn(ipnRequest);
        return ResponseEntity.noContent().build();
    }

    // Dùng cho trang web hiển thị kết quả sau khi MoMo redirect về (public, xác thực bằng signature
    // của MoMo thay vì JWT, vì người thanh toán có thể không đăng nhập web này).
    @GetMapping("return-info")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getReturnInfo(@ModelAttribute MomoIpnRequest returnParams) {

        PaymentStatusResponse response = momoService.getReturnInfo(returnParams);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("status/{orderId}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> checkPaymentStatus(
            @PathVariable UUID orderId,
            Authentication authentication) {

        String email = authentication.getName();
        PaymentStatusResponse response = momoService.checkPaymentStatus(email, orderId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
