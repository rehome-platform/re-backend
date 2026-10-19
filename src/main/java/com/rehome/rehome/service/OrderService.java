package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.response.OrderResponse;

import java.util.List;

public interface OrderService {
    List<OrderResponse> createOrders(String email, CreateOrderRequest request);
}
