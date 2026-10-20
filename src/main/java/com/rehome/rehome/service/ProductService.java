package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.MyProductResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ProductDetailResponse;
import com.rehome.rehome.dto.response.ProductSummaryResponse;
import com.rehome.rehome.entity.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface ProductService {
    PageResponse<ProductSummaryResponse> getProducts(String search, UUID categoryId, UUID brandId,
                                                       BigDecimal priceMin, BigDecimal priceMax,
                                                       UUID warehouseId, int page, int size);

    ProductDetailResponse getProductDetail(String id);

    Page<MyProductResponse> getMyProducts(UUID memberId, ProductStatus status, Pageable pageable);
}
