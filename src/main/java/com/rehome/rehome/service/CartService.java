package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.AddCartItemRequest;
import com.rehome.rehome.dto.response.CartItemResponse;

public interface CartService {
    CartItemResponse addItem(String email, AddCartItemRequest request);
}
