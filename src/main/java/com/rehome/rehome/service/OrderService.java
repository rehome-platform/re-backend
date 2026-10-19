package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.response.OrderCancelResponse;
import com.rehome.rehome.dto.response.OrderDetailResponse;
import com.rehome.rehome.dto.response.OrderListResponse;
import com.rehome.rehome.dto.response.OrderResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    List<OrderResponse> createOrders(String email, CreateOrderRequest request);

    OrderListResponse getOrders(String email, String status, int page, int size);

    OrderDetailResponse getOrderDetail(String email, UUID orderId);

    OrderCancelResponse cancelOrder(String email, UUID orderId);
}
