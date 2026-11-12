package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateProductRequest;
import com.rehome.rehome.dto.request.DonateProductRequest;
import com.rehome.rehome.dto.request.PriceReductionRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.TryOnSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface ProductService {

    // ---- Sàn (khách xem được, viewerId = null nếu chưa đăng nhập)
    PageResponse<ProductSummaryResponse> getProducts(String search, UUID categoryId, UUID brandId,
                                                     BigDecimal priceMin, BigDecimal priceMax,
                                                     UUID warehouseId, TryOnSlot tryonSlot,
                                                     int page, int size, UUID viewerId);

    ProductDetailResponse getProductDetail(String id, UUID viewerId);

    // ---- Hàng ký gửi của tôi (Member)
    Page<MyProductResponse> getMyProducts(UUID memberId, ProductStatus status,
                                          boolean pendingDecision, Pageable pageable);

    MyProductResponse getMyProduct(UUID memberId, String id);

    PriceReductionResponse reducePrice(Account account, String productId, PriceReductionRequest request);

    // POST /products/{id}/donate: quyên góp sản phẩm ký gửi còn đủ điều kiện
    DonateProductResponse donate(Account account, String productId, DonateProductRequest request);

    ShippingFeePreviewResponse getShippingFeePreview(String productId, String wardName, String provinceName);

    // ---- Kho (Moderator)
    ProductResponse create(CreateProductRequest request, Account account);

    ProductSkuLookupResponse getBySku(Account account, String sku);

    PdfDownload getSkuLabel(Account account, String productId);

    PageResponse<InventoryItemResponse> getInventory(Account account, ProductStatus status,
                                                     boolean degradedOnly, int page, int size);
}