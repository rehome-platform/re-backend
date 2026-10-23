package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateBrandRequest;
import com.rehome.rehome.dto.request.UpdateBrandRequest;
import com.rehome.rehome.dto.response.BrandResponse;
import com.rehome.rehome.dto.response.CatalogStatusResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.List;
import java.util.UUID;

public interface BrandService {
    BrandResponse create(CreateBrandRequest request);
    BrandResponse update(UUID id, UpdateBrandRequest request);
    CatalogStatusResponse hide(UUID id);
    List<BrandResponse> list(String status, Account account);
}
