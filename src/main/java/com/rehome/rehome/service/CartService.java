package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.AddCartItemRequest;
import com.rehome.rehome.dto.response.CartItemResponse;
import com.rehome.rehome.dto.response.CartResponse;

public interface CartService {
    CartItemResponse addItem(String email, AddCartItemRequest request);

    CartResponse getCart(String email);
}
