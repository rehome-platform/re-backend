package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.request.PackScanRequest;
import com.rehome.rehome.dto.request.PayOrdersRequest;
import com.rehome.rehome.dto.request.RateOrderRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CreateShipmentResponse;
import com.rehome.rehome.dto.response.OrderCancelResponse;
import com.rehome.rehome.dto.response.OrderConfirmReceiptResponse;
import com.rehome.rehome.dto.response.OrderDetailResponse;
import com.rehome.rehome.dto.response.OrderListResponse;
import com.rehome.rehome.dto.response.OrderRatingResponse;
import com.rehome.rehome.dto.response.OrderResponse;
import com.rehome.rehome.dto.response.OrderTrackingResponse;
import com.rehome.rehome.dto.response.PackScanResponse;
import com.rehome.rehome.dto.response.PayOrdersResponse;
import com.rehome.rehome.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> createOrders(
            Authentication authentication,
            @RequestBody CreateOrderRequest request) {

        String email = authentication.getName();
        List<OrderResponse> orders = orderService.createOrders(email, request);

        ApiResponse<List<OrderResponse>> response = ApiResponse.<List<OrderResponse>>builder()
                .result(orders)
                .isSuccess(true)
                .statusCode(201)
                .message("Đặt hàng thành công, vui lòng thanh toán")
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<OrderListResponse>> getOrders(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String email = authentication.getName();
        OrderListResponse result = orderService.getOrders(email, status, page, size);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrderDetail(
            Authentication authentication,
            @PathVariable UUID id) {

        String email = authentication.getName();
        OrderDetailResponse result = orderService.getOrderDetail(email, id);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<OrderCancelResponse>> cancelOrder(
            Authentication authentication,
            @PathVariable UUID id) {

        String email = authentication.getName();
        OrderCancelResponse result = orderService.cancelOrder(email, id);

        String message = result.getRefundAmount() != null
                ? "Đã hủy đơn, hoàn tiền vào ví"
                : "Đã hủy đơn";

        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    @PostMapping("/pay")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PayOrdersResponse>> payOrders(
            Authentication authentication,
            @RequestBody PayOrdersRequest request) {

        String email = authentication.getName();
        PayOrdersResponse result = orderService.payOrders(email, request);

        return ResponseEntity.ok(ApiResponse.success(result, "Thanh toán bằng ví thành công"));
    }

    @PostMapping("/{id}/confirm-receipt")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<OrderConfirmReceiptResponse>> confirmReceipt(
            Authentication authentication,
            @PathVariable UUID id) {

        String email = authentication.getName();
        OrderConfirmReceiptResponse result = orderService.confirmReceipt(email, id);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã xác nhận nhận hàng"));
    }

    @PostMapping("/{id}/rating")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<OrderRatingResponse>> rateOrder(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody RateOrderRequest request) {

        String email = authentication.getName();
        OrderRatingResponse result = orderService.rateOrder(email, id, request);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã ghi nhận đánh giá"));
    }

    // API 69 (F-127)
    @PostMapping("/{id}/pack/scan")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<PackScanResponse>> packScan(
            @PathVariable UUID id,
            @RequestBody PackScanRequest request) {

        PackScanResponse result = orderService.packScan(id, request);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // API 70 (F-127)
    @PostMapping("/{id}/shipment")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<CreateShipmentResponse>> createShipment(@PathVariable UUID id) {

        CreateShipmentResponse result = orderService.createShipment(id);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã tạo vận đơn"));
    }

    // API 64 (F-060)
    @GetMapping("/{id}/tracking")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<OrderTrackingResponse>> getTracking(
            Authentication authentication,
            @PathVariable UUID id) {

        String email = authentication.getName();
        OrderTrackingResponse result = orderService.getTracking(email, id);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
