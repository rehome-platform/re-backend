package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CreateVnPayResponse;
import com.rehome.rehome.dto.response.PaymentStatusResponse;
import com.rehome.rehome.dto.response.VnPayIpnResult;
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

    @PostMapping("create")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<CreateVnPayResponse>> createPayment(
            @RequestParam UUID orderId,
            @RequestParam(required = false) String returnUrl,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String email = authentication.getName();
        CreateVnPayResponse response = vnPayService.createPaymentForOrder(
                email, orderId, returnUrl, clientIp(httpRequest));

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // VNPay gọi server-to-server sau khi thanh toán xong; phải trả đúng format {"RspCode","Message"}
    // theo tài liệu tích hợp VNPay, nếu không VNPay sẽ coi là xác nhận thất bại và gọi lại nhiều lần.
    @GetMapping("ipn-handler")
    public ResponseEntity<Map<String, String>> ipnHandler(@RequestParam Map<String, String> params) {

        VnPayIpnResult result = vnPayService.handleIpn(params);

        Map<String, String> body = new HashMap<>();
        body.put("RspCode", result.rspCode());
        body.put("Message", result.message());
        return ResponseEntity.ok(body);
    }

    // Dùng cho trang web hiển thị kết quả sau khi VNPay redirect về (public, xác thực bằng
    // vnp_SecureHash của VNPay thay vì JWT, vì người thanh toán có thể không đăng nhập web này).
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

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
