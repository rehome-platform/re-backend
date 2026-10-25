package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.AddCartItemRequest;
import com.rehome.rehome.dto.response.CartItemResponse;
import com.rehome.rehome.dto.response.CartResponse;
import com.rehome.rehome.dto.response.CheckoutPreviewResponse;

import java.util.UUID;

public interface CartService {
    CartItemResponse addItem(String email, AddCartItemRequest request);

    CartResponse getCart(String email);

    void deleteItem(String email, UUID id);

    CheckoutPreviewResponse checkoutPreview(String email, String shippingAddressId);
}
