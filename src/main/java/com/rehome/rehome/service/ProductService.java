package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ProductSummaryResponse;

import java.math.BigDecimal;
import java.util.UUID;

public interface ProductService {
    PageResponse<ProductSummaryResponse> getProducts(String search, UUID categoryId, UUID brandId,
                                                       BigDecimal priceMin, BigDecimal priceMax,
                                                       UUID warehouseId, int page, int size);
}
