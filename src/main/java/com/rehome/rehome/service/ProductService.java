package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateProductRequest;
import com.rehome.rehome.dto.request.PriceReductionRequest;
import com.rehome.rehome.dto.request.UpdateShelfLocationRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ProductService {
    PageResponse<ProductSummaryResponse> getProducts(String search, UUID categoryId, UUID brandId,
                                                       BigDecimal priceMin, BigDecimal priceMax,
                                                       UUID warehouseId, int page, int size);

    ProductDetailResponse getProductDetail(String id);
    ProductEndOfTermDetailResponse getProductById(String id);
    Page<MyProductResponse> getMyProducts(UUID memberId, ProductStatus status, Pageable pageable);
    ProductResponse create(CreateProductRequest request, Account account);

    List<StockMovementResponse> getStockMovements(String productId);

    PageResponse<InventoryItemResponse> getInventory(Account account, ProductStatus status,
                                                     String shelfLocation, int page, int size);
    // Tem SKU trả binary PDF, không lưu file (render lại được từ Product bất cứ lúc nào)
    PdfDownload getSkuLabel(Account account, String productId);
    ProductSkuLookupResponse getBySku(Account account, String sku);
    ShelfLocationResponse updateShelfLocation(Account account, String productId, UpdateShelfLocationRequest request);
    PriceReductionResponse reducePrice(Account account, String productId, PriceReductionRequest request);

    // Xem trước phí ship GHN thật cho 1 sản phẩm, chỉ cần nhập địa chỉ - không cần giỏ hàng/đơn hàng
    ShippingFeePreviewResponse getShippingFeePreview(String productId, String wardName, String provinceName);
}
