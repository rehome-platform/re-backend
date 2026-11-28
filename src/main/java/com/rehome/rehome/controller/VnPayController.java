package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateGatewayPaymentRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CreateVnPayResponse;
import com.rehome.rehome.dto.response.PaymentStatusResponse;
import com.rehome.rehome.dto.response.ShipBackPaymentStatusResponse;
import com.rehome.rehome.dto.response.VnPayIpnResult;
import com.rehome.rehome.service.ReturnShipBackService;
import com.rehome.rehome.service.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/vnpay")
public class VnPayController {

    private final VnPayService vnPayService;
    private final ReturnShipBackService returnShipBackService;

    @PostMapping("create")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<CreateVnPayResponse>> createPayment(
            @RequestBody CreateGatewayPaymentRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String email = authentication.getName();
        CreateVnPayResponse response = vnPayService.createPaymentForOrders(
                email, request.toOrderIds(), request.getRedirectUrl(), clientIp(httpRequest));

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("ipn-handler")
    public ResponseEntity<Map<String, String>> ipnHandler(@RequestParam Map<String, String> params) {

        VnPayIpnResult result = vnPayService.handleIpn(params);

        Map<String, String> body = new HashMap<>();
        body.put("RspCode", result.rspCode());
        body.put("Message", result.message());
        return ResponseEntity.ok(body);
    }

    @GetMapping("return-info")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getReturnInfo(@RequestParam Map<String, String> params) {

        PaymentStatusResponse response = vnPayService.getReturnInfo(params);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("status/{orderId}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> checkPaymentStatus(
            @PathVariable UUID orderId,
            Authentication authentication) {

        String email = authentication.getName();
        PaymentStatusResponse response = vnPayService.checkPaymentStatus(email, orderId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("membership/status/{paymentId}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<com.rehome.rehome.dto.response.MembershipPaymentStatusResponse>>
    checkMembershipPayment(@PathVariable UUID paymentId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.rehome.rehome.entity.auth.Account account) {
        return ResponseEntity.ok(ApiResponse.success(vnPayService.checkMembershipPayment(account.getId(), paymentId)));
    }

    @GetMapping("return-ship-back/status/{returnRequestId}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<ShipBackPaymentStatusResponse>> checkReturnShipBackPayment(
            @PathVariable UUID returnRequestId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                returnShipBackService.syncPaymentStatus(authentication.getName(), returnRequestId)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
