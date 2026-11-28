package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.AddressUnitResponse;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.service.AddressUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/address-units")
@RequiredArgsConstructor
public class AddressUnitController {

    private final AddressUnitService addressUnitService;

    @GetMapping("/provinces")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AddressUnitResponse>>> getProvinces() {
        return ResponseEntity.ok(ApiResponse.success(addressUnitService.getProvinces()));
    }

    @GetMapping("/wards")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AddressUnitResponse>>> getWards(
            @RequestParam int provinceId) {
        return ResponseEntity.ok(ApiResponse.success(addressUnitService.getWards(provinceId)));
    }
}
