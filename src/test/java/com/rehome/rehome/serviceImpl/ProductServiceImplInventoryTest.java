package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.UpdateShelfLocationRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.StockMovementType;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.StockMovementLog;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.StockMovementLogRepository;
import com.rehome.rehome.service.SkuLabelPdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplInventoryTest {

    private static final String SKU = "Q7-AK-000845";

    @Mock private ProductRepository productRepository;
    @Mock private StockMovementLogRepository stockMovementLogRepository;
    @Mock private SkuLabelPdfService skuLabelPdfService;

    // Các dependency còn lại của ProductServiceImpl không dùng trong test này, Mockito truyền null
    @InjectMocks
    private ProductServiceImpl productService;

    private Warehouse warehouse;
    private Warehouse otherWarehouse;
    private Account moderator;
    private Product product;

    @BeforeEach
    void setUp() {
        warehouse = Warehouse.builder().id(UUID.randomUUID()).build();
        otherWarehouse = Warehouse.builder().id(UUID.randomUUID()).build();

        moderator = Account.builder()
                .id(UUID.randomUUID())
                .role(Role.MODERATOR)
                .warehouse(warehouse)
                .build();

        product = Product.builder()
                .id(SKU)
                .name("Áo sơ mi Zara trắng")
                .warehouse(warehouse)
                .status(ProductStatus.ON_SALE)
                .shelfLocation("A1-03")
                .build();
    }

    private Account moderatorOf(Warehouse w) {
        return Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).warehouse(w).build();
    }

    // ==================== API 41: getBySku ====================

    @Test
    @DisplayName("getBySku: cùng kho -> trả đúng thông tin")
    void getBySku_success() {
        when(productRepository.findById(SKU)).thenReturn(Optional.of(product));

        ProductSkuLookupResponse res = productService.getBySku(moderator, SKU);

        assertEquals(SKU, res.getId());
        assertEquals("Áo sơ mi Zara trắng", res.getName());
        assertEquals("A1-03", res.getShelfLocation());
        assertEquals(ProductStatus.ON_SALE, res.getStatus());
    }

    @Test
    @DisplayName("getBySku: không có SKU -> PRODUCT_NOT_FOUND")
    void getBySku_notFound() {
        when(productRepository.findById(SKU)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> productService.getBySku(moderator, SKU));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("getBySku: SKU thuộc kho khác -> PRODUCT_NOT_IN_YOUR_WAREHOUSE")
    void getBySku_otherWarehouse() {
        when(productRepository.findById(SKU)).thenReturn(Optional.of(product));

        AppException ex = assertThrows(AppException.class,
                () -> productService.getBySku(moderatorOf(otherWarehouse), SKU));

        assertEquals(ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE, ex.getErrorCode());
    }

    @Test
    @DisplayName("getBySku: Moderator chưa gán kho -> PRODUCT_NOT_IN_YOUR_WAREHOUSE")
    void getBySku_moderatorWithoutWarehouse() {
        when(productRepository.findById(SKU)).thenReturn(Optional.of(product));

        AppException ex = assertThrows(AppException.class,
                () -> productService.getBySku(moderatorOf(null), SKU));

        assertEquals(ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE, ex.getErrorCode());
    }

    // ==================== API 39: getSkuLabel ====================

    @Test
    @DisplayName("getSkuLabel: thành công -> trả URL do SkuLabelPdfService sinh")
    void getSkuLabel_success() {
        when(productRepository.findById(SKU)).thenReturn(Optional.of(product));
        when(skuLabelPdfService.generate(product)).thenReturn("https://cdn.rehome.vn/labels/" + SKU + ".pdf");

        SkuLabelResponse res = productService.getSkuLabel(moderator, SKU);

        assertEquals("https://cdn.rehome.vn/labels/" + SKU + ".pdf", res.getLabelPdfUrl());
    }

    @Test
    @DisplayName("getSkuLabel: không có sản phẩm -> PRODUCT_NOT_FOUND, không sinh PDF")
    void getSkuLabel_notFound() {
        when(productRepository.findById(SKU)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> productService.getSkuLabel(moderator, SKU));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(skuLabelPdfService);
    }

    @Test
    @DisplayName("getSkuLabel: kho khác -> 403, không sinh PDF")
    void getSkuLabel_otherWarehouse() {
        when(productRepository.findById(SKU)).thenReturn(Optional.of(product));

        AppException ex = assertThrows(AppException.class,
                () -> productService.getSkuLabel(moderatorOf(otherWarehouse), SKU));

        assertEquals(ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE, ex.getErrorCode());
        verifyNoInteractions(skuLabelPdfService);
    }

    // ==================== API 42: updateShelfLocation ====================

    private UpdateShelfLocationRequest shelfRequest(String value) {
        UpdateShelfLocationRequest req = new UpdateShelfLocationRequest();
        req.setShelfLocation(value);
        return req;
    }

    @Test
    @DisplayName("updateShelfLocation: đổi kệ -> cập nhật product và ghi log SHELF_MOVE")
    void updateShelfLocation_success() {
        when(productRepository.findByIdForUpdate(SKU)).thenReturn(Optional.of(product));

        ShelfLocationResponse res = productService.updateShelfLocation(moderator, SKU, shelfRequest("  B2-10  "));

        assertEquals(SKU, res.getId());
        assertEquals("B2-10", res.getShelfLocation());
        assertEquals("B2-10", product.getShelfLocation());
        verify(productRepository).save(product);

        ArgumentCaptor<StockMovementLog> captor = ArgumentCaptor.forClass(StockMovementLog.class);
        verify(stockMovementLogRepository).save(captor.capture());
        StockMovementLog log = captor.getValue();
        assertEquals(StockMovementType.SHELF_MOVE, log.getMovementType());
        assertEquals("A1-03", log.getFromLocation());
        assertEquals("B2-10", log.getToLocation());
        assertSame(product, log.getProduct());
        assertSame(warehouse, log.getWarehouse());
        assertSame(moderator, log.getModerator());
    }

    @Test
    @DisplayName("updateShelfLocation: vị trí không đổi -> trả 200, không save, không ghi log")
    void updateShelfLocation_sameLocation() {
        when(productRepository.findByIdForUpdate(SKU)).thenReturn(Optional.of(product));

        ShelfLocationResponse res = productService.updateShelfLocation(moderator, SKU, shelfRequest(" A1-03 "));

        assertEquals("A1-03", res.getShelfLocation());
        verify(productRepository, never()).save(any());
        verifyNoInteractions(stockMovementLogRepository);
    }

    @Test
    @DisplayName("updateShelfLocation: sản phẩm chưa có kệ (null) -> ghi log với fromLocation null")
    void updateShelfLocation_fromNull() {
        product.setShelfLocation(null);
        when(productRepository.findByIdForUpdate(SKU)).thenReturn(Optional.of(product));

        productService.updateShelfLocation(moderator, SKU, shelfRequest("B2-10"));

        ArgumentCaptor<StockMovementLog> captor = ArgumentCaptor.forClass(StockMovementLog.class);
        verify(stockMovementLogRepository).save(captor.capture());
        assertNull(captor.getValue().getFromLocation());
        assertEquals("B2-10", captor.getValue().getToLocation());
    }

    @Test
    @DisplayName("updateShelfLocation: không có sản phẩm -> PRODUCT_NOT_FOUND")
    void updateShelfLocation_notFound() {
        when(productRepository.findByIdForUpdate(SKU)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> productService.updateShelfLocation(moderator, SKU, shelfRequest("B2-10")));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(stockMovementLogRepository);
    }

    @Test
    @DisplayName("updateShelfLocation: kho khác -> 403, không đổi kệ, không ghi log")
    void updateShelfLocation_otherWarehouse() {
        when(productRepository.findByIdForUpdate(SKU)).thenReturn(Optional.of(product));

        AppException ex = assertThrows(AppException.class,
                () -> productService.updateShelfLocation(moderatorOf(otherWarehouse), SKU, shelfRequest("B2-10")));

        assertEquals(ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE, ex.getErrorCode());
        assertEquals("A1-03", product.getShelfLocation());
        verify(productRepository, never()).save(any());
        verifyNoInteractions(stockMovementLogRepository);
    }

    // ==================== API 43: getStockMovements ====================

    @Test
    @DisplayName("getStockMovements: trả đúng danh sách đã map")
    void getStockMovements_success() {
        UUID logId = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-01T09:00:00Z");
        StockMovementLog log = StockMovementLog.builder()
                .id(logId)
                .movementType(StockMovementType.STOCK_IN)
                .toLocation("A1-03")
                .note("Nhập kho")
                .createdAt(at)
                .build();

        when(productRepository.existsById(SKU)).thenReturn(true);
        when(stockMovementLogRepository.findByProduct_IdOrderByCreatedAtDesc(SKU)).thenReturn(List.of(log));

        List<StockMovementResponse> res = productService.getStockMovements(SKU);

        assertEquals(1, res.size());
        StockMovementResponse r = res.get(0);
        assertEquals(logId, r.getId());
        assertEquals(StockMovementType.STOCK_IN, r.getType());
        assertNull(r.getFromLocation());
        assertEquals("A1-03", r.getToLocation());
        assertEquals("Nhập kho", r.getNote());
        assertEquals(at, r.getCreatedAt());
    }

    @Test
    @DisplayName("getStockMovements: sản phẩm chưa có log -> danh sách rỗng")
    void getStockMovements_empty() {
        when(productRepository.existsById(SKU)).thenReturn(true);
        when(stockMovementLogRepository.findByProduct_IdOrderByCreatedAtDesc(SKU)).thenReturn(List.of());

        assertTrue(productService.getStockMovements(SKU).isEmpty());
    }

    @Test
    @DisplayName("getStockMovements: không có sản phẩm -> PRODUCT_NOT_FOUND, không query log")
    void getStockMovements_notFound() {
        when(productRepository.existsById(SKU)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> productService.getStockMovements(SKU));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(stockMovementLogRepository);
    }

    // ==================== API 40: getInventory ====================

    private Pageable captureInventoryPageable(String expectedShelf, ProductStatus expectedStatus) {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findInventory(
                eq(warehouse.getId()), eq(expectedStatus), eq(expectedShelf), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("getInventory: lọc theo kho của Moderator, map đúng field")
    void getInventory_success() {
        when(productRepository.findInventory(eq(warehouse.getId()), eq(ProductStatus.ON_SALE), eq("A1"), any()))
                .thenReturn(new PageImpl<>(List.of(product)));

        PageResponse<InventoryItemResponse> res =
                productService.getInventory(moderator, ProductStatus.ON_SALE, "A1", 0, 20);

        // Giả định PageResponse có getter getContent() (theo field "content" trong spec)
        assertEquals(1, res.getContent().size());
        InventoryItemResponse item = res.getContent().get(0);
        assertEquals(SKU, item.getId());
        assertEquals("Áo sơ mi Zara trắng", item.getName());
        assertEquals("A1-03", item.getShelfLocation());
        assertEquals(ProductStatus.ON_SALE, item.getStatus());
    }

    @Test
    @DisplayName("getInventory: Moderator chưa gán kho -> MODERATOR_NO_WAREHOUSE")
    void getInventory_noWarehouse() {
        AppException ex = assertThrows(AppException.class,
                () -> productService.getInventory(moderatorOf(null), null, null, 0, 20));

        assertEquals(ErrorCode.MODERATOR_NO_WAREHOUSE, ex.getErrorCode());
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("getInventory: shelfLocation rỗng/khoảng trắng -> coi như không lọc (null)")
    void getInventory_blankShelfBecomesNull() {
        when(productRepository.findInventory(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        productService.getInventory(moderator, null, "   ", 0, 20);

        captureInventoryPageable(null, null);
    }

    @Test
    @DisplayName("getInventory: shelfLocation được trim")
    void getInventory_shelfTrimmed() {
        when(productRepository.findInventory(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        productService.getInventory(moderator, null, "  A1  ", 0, 20);

        captureInventoryPageable("A1", null);
    }

    @Test
    @DisplayName("getInventory: page âm -> 0, size < 1 -> mặc định 20")
    void getInventory_pagingDefaults() {
        when(productRepository.findInventory(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        productService.getInventory(moderator, null, null, -5, 0);

        Pageable pageable = captureInventoryPageable(null, null);
        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
    }

    @Test
    @DisplayName("getInventory: size vượt trần -> bị chặn ở 50")
    void getInventory_sizeCapped() {
        when(productRepository.findInventory(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        productService.getInventory(moderator, null, null, 2, 1000);

        Pageable pageable = captureInventoryPageable(null, null);
        assertEquals(2, pageable.getPageNumber());
        assertEquals(50, pageable.getPageSize());
    }

    @Test
    @DisplayName("getInventory: sắp xếp theo createdAt giảm dần")
    void getInventory_sortByCreatedAtDesc() {
        when(productRepository.findInventory(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        productService.getInventory(moderator, null, null, 0, 20);

        Pageable pageable = captureInventoryPageable(null, null);
        assertNotNull(pageable.getSort().getOrderFor("createdAt"));
        assertTrue(pageable.getSort().getOrderFor("createdAt").isDescending());
    }
}