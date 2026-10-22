package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateBrandRequest;
import com.rehome.rehome.dto.request.UpdateBrandRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.BrandResponse;
import com.rehome.rehome.dto.response.CatalogStatusResponse;
import com.rehome.rehome.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @PostMapping
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<BrandResponse>> create(
            @Valid @RequestBody CreateBrandRequest request) {
        BrandResponse result = brandService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, 201, "Đã tạo thương hiệu"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<BrandResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBrandRequest request) {
        return ResponseEntity.ok(ApiResponse.success(brandService.update(id, request), 200, "Đã cập nhật"));
    }
    // BrandController (@RequestMapping("/brands"))
    @PatchMapping("/{id}/hide")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<CatalogStatusResponse>> hide(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(brandService.hide(id), 200, "Đã ẩn"));
    }
}