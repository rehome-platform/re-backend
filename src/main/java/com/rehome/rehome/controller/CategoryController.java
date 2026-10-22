package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateCategoryRequest;
import com.rehome.rehome.dto.request.UpdateCategoryRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CatalogStatusResponse;
import com.rehome.rehome.dto.response.CategoryResponse;
import com.rehome.rehome.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(categoryService.create(request), 201, "Đã tạo danh mục"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.update(id, request), 200, "Đã cập nhật"));
    }
    // CategoryController (@RequestMapping("/categories"))
    @PatchMapping("/{id}/hide")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<CatalogStatusResponse>> hide(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.hide(id), 200, "Đã ẩn"));
    }
}