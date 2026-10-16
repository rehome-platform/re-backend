package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.AddCartItemRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CartItemResponse;
import com.rehome.rehome.dto.response.CartResponse;
import com.rehome.rehome.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(Authentication authentication) {

        String email = authentication.getName();
        CartResponse cart = cartService.getCart(email);

        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @PostMapping("/items")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<CartItemResponse>> addItem(
            Authentication authentication,
            @RequestBody AddCartItemRequest request) {

        String email = authentication.getName();
        CartItemResponse item = cartService.addItem(email, request);

        ApiResponse<CartItemResponse> response = ApiResponse.success(item, "Đã thêm vào giỏ");

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            Authentication authentication,
            @PathVariable UUID id) {

        String email = authentication.getName();
        cartService.deleteItem(email, id);

        ApiResponse<Void> response = ApiResponse.success(null, "Đã xóa khỏi giỏ");

        return ResponseEntity.ok(response);
    }
}
