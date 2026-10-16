package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.AddShippingAddressRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CreateShippingAddressResponse;
import com.rehome.rehome.dto.response.ShippingAddressResponse;
import com.rehome.rehome.service.ShippingAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
public class ShippingAddressController {

    private final ShippingAddressService shippingAddressService;

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<ShippingAddressResponse>>> getAddresses(Authentication authentication) {

        String email = authentication.getName();
        List<ShippingAddressResponse> addresses = shippingAddressService.getAddresses(email);

        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    @PostMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<CreateShippingAddressResponse>> createAddress(
            Authentication authentication,
            @RequestBody AddShippingAddressRequest request) {

        String email = authentication.getName();
        CreateShippingAddressResponse address = shippingAddressService.createAddress(email, request);

        ApiResponse<CreateShippingAddressResponse> response = ApiResponse.success(address, "Đã thêm địa chỉ");

        return ResponseEntity.ok(response);
    }
}
