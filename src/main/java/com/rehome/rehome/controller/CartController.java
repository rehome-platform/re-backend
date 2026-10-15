package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.AddCartItemRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CartItemResponse;
import com.rehome.rehome.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/items")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<CartItemResponse>> addItem(
            Authentication authentication,
            @RequestBody AddCartItemRequest request) {

        String email = authentication.getName();
        CartItemResponse item = cartService.addItem(email, request);

        ApiResponse<CartItemResponse> response = ApiResponse.<CartItemResponse>builder()
                .result(item)
                .isSuccess(true)
                .statusCode(HttpStatus.CREATED.value())
                .message("Đã thêm vào giỏ")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
