package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateBrandRequest;
import com.rehome.rehome.dto.request.UpdateBrandRequest;
import com.rehome.rehome.dto.response.BrandResponse;
import com.rehome.rehome.dto.response.CatalogStatusResponse;

import java.util.UUID;

public interface BrandService {
    BrandResponse create(CreateBrandRequest request);
    BrandResponse update(UUID id, UpdateBrandRequest request);
    // CategoryService
    CatalogStatusResponse hide(UUID id);
}
