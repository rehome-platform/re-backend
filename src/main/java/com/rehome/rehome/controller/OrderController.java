package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.OrderListResponse;
import com.rehome.rehome.dto.response.OrderResponse;
import com.rehome.rehome.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
