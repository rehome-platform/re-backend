package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateProductRequest;
import com.rehome.rehome.dto.request.PriceReductionRequest;
import com.rehome.rehome.dto.request.UpdateShelfLocationRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<ProductSummaryResponse> result = productService.getProducts(
                search, categoryId, brandId, priceMin, priceMax, warehouseId, page, size);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductDetail(@PathVariable String id) {
        ProductDetailResponse result = productService.getProductDetail(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('MEMBER')")
    public ApiResponse<PageResponse<MyProductResponse>> getMyProducts(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Account account) {

        if(account == null){
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MyProductResponse> result = productService.getMyProducts(account.getId(), status, pageable);
        return ApiResponse.success(PageResponse.from(result));
    }
    @GetMapping("/mine/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ApiResponse<ProductEndOfTermDetailResponse> getProductById(@PathVariable String id) {
        return ApiResponse.success(productService.getProductById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @AuthenticationPrincipal Account account,
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse result = productService.create(request, account);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, 201, "Đã nhập kho"));
    }

    @PostMapping("/{id}/price-reduction")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PriceReductionResponse>> reducePrice(
            @AuthenticationPrincipal Account account,
            @PathVariable String id,
            @Valid @RequestBody PriceReductionRequest request) {

        PriceReductionResponse result = productService.reducePrice(account, id, request);
        return ResponseEntity.ok(ApiResponse.success(result, 200, "Đã giảm giá sản phẩm"));
    }

    @GetMapping("/{id}/stock-movements")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> getStockMovements(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getStockMovements(id), 200, ""));
    }

    @PatchMapping("/{id}/shelf-location")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ShelfLocationResponse>> updateShelfLocation(
            @AuthenticationPrincipal Account account,
            @PathVariable String id,
            @Valid @RequestBody UpdateShelfLocationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.updateShelfLocation(account, id, request), 200, "Đã cập nhật vị trí kệ"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getProduct(
            @PathVariable String id,
            @AuthenticationPrincipal Account account) {

        if (account != null && account.getRole() == Role.MODERATOR) {
            return ResponseEntity.ok(ApiResponse.<Object>success(productService.getBySku(account, id)));
        }
        return ResponseEntity.ok(ApiResponse.<Object>success(productService.getProductDetail(id)));
    }
    @GetMapping("/{id}/sku-label")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<SkuLabelResponse>> getSkuLabel(
            @AuthenticationPrincipal Account account,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getSkuLabel(account, id), 200, ""));
    }

    // Xem trước phí ship GHN thật cho 1 sản phẩm - chỉ cần nhập địa chỉ, không cần giỏ hàng/đơn hàng
    @GetMapping("/{id}/shipping-fee-preview")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<ShippingFeePreviewResponse>> getShippingFeePreview(
            @PathVariable String id,
            @RequestParam String wardName,
            @RequestParam String provinceName) {

        ShippingFeePreviewResponse result = productService.getShippingFeePreview(id, wardName, provinceName);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
