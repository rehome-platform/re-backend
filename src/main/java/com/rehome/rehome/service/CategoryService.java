package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateCategoryRequest;
import com.rehome.rehome.dto.request.UpdateCategoryRequest;
import com.rehome.rehome.dto.response.CatalogStatusResponse;
import com.rehome.rehome.dto.response.CategoryResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.List;
import java.util.UUID;

public interface CategoryService {
    CategoryResponse create(CreateCategoryRequest request);
    CategoryResponse update(UUID id, UpdateCategoryRequest request);
    // CategoryService
    CatalogStatusResponse hide(UUID id);
    List<CategoryResponse> list(String status, Account account);
}
