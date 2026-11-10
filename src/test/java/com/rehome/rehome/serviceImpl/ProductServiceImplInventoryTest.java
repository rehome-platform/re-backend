package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.entity.marketplace.Category;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplInventoryTest {

    private static final String SKU = "Q7-AK-000845";

    @Mock private ProductRepository productRepository;
    @Mock private ProductPhotoRepository productPhotoRepository;
    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private SkuLabelPdfService skuLabelPdfService;

    // Các dependency còn lại của ProductServiceImpl không dùng trong test này, Mockito truyền null
    @InjectMocks
    private ProductServiceImpl productService;

    private Warehouse warehouse;
    private Warehouse otherWarehouse;
    private Account moderator;
    private Product product;
    private Account member;
    private Instant createdAt;

    @BeforeEach
    void setUp() {
        warehouse = Warehouse.builder().id(UUID.randomUUID()).build();
        otherWarehouse = Warehouse.builder().id(UUID.randomUUID()).build();

        moderator = Account.builder()
                .id(UUID.randomUUID())
                .role(Role.MODERATOR)
                .warehouse(warehouse)
                .build();

        member = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
        createdAt = Instant.now().minus(Duration.ofDays(10));

        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .agreedPrice(new BigDecimal("500000"))
                .floorPrice(new BigDecimal("300000"))
                .build();

        product = Product.builder()
                .id(SKU)
                .name("Áo sơ mi Zara trắng")
                .warehouse(warehouse)
                .status(ProductStatus.ON_SALE)
                .brand(Brand.builder().name("Zara").build())
                .category(Category.builder().name("Áo sơ mi").build())
                .member(member)
                .consignmentReceipt(receipt)
                .size("M")
                .conditionNote("Còn mới 95%")
                .currentPrice(new BigDecimal("450000"))
                .currentTerm((short) 2)
                .termStartedAt(Instant.now().minus(Duration.ofDays(5)))
                .createdAt(createdAt)
                .build();
    }

    private Account moderatorOf(Warehouse w) {
        return Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).warehouse(w).build();
    }

    // ==================== API 41: getBySku ====================

    @Test
    @DisplayName("getBySku: cùng kho -> trả đủ thông tin chi tiết")
    void getBySku_success() {
        MemberProfile profile = MemberProfile.builder()
                .accountId(member.getId()).fullName("Nguyễn Văn A").memberCode("MB-000123").build();
        ProductPhoto p1 = ProductPhoto.builder().imageUrl("https://img/1.jpg").displayOrder(0).build();
        ProductPhoto p2 = ProductPhoto.builder().imageUrl("https://img/2.jpg").displayOrder(1).build();

        when(productRepository.findById(SKU)).thenReturn(Optional.of(product));
        when(productPhotoRepository.findByProduct_IdOrderByDisplayOrderAscIdAsc(SKU)).thenReturn(List.of(p1, p2));
        when(memberProfileRepository.findByAccountId(member.getId())).thenReturn(Optional.of(profile));

        ProductSkuLookupResponse res = productService.getBySku(moderator, SKU);

        assertEquals(SKU, res.getId());
        assertEquals("Áo sơ mi Zara trắng", res.getName());
        assertEquals(ProductStatus.ON_SALE, res.getStatus());
        assertEquals("Zara", res.getBrandName());
        assertEquals("Áo sơ mi", res.getCategoryName());
        assertEquals("M", res.getSize());
        assertEquals("Còn mới 95%", res.getConditionNote());
        assertEquals(new BigDecimal("450000"), res.getCurrentPrice());
        assertEquals(new BigDecimal("500000"), res.getOriginalPrice());
        assertEquals(new BigDecimal("300000"), res.getFloorPrice());
        assertEquals((short) 2, res.getCurrentTerm());
        assertEquals(5, res.getDaysIntoTerm());
        assertEquals("MB-000123", res.getMemberCode());
        assertEquals("Nguyễn Văn A", res.getMemberFullName());
        assertEquals(createdAt, res.getStockInDate());
        assertEquals(List.of("https://img/1.jpg", "https://img/2.jpg"), res.getPhotos());
    }

    @Test
    @DisplayName("getBySku: vừa bắt đầu kỳ (< 1 ngày) -> daysIntoTerm tối thiểu là 1")
    void getBySku_daysIntoTermMinimumOne() {
        product.setTermStartedAt(Instant.now());
        when(productRepository.findById(SKU)).thenReturn(Optional.of(product));
        when(productPhotoRepository.findByProduct_IdOrderByDisplayOrderAscIdAsc(SKU)).thenReturn(List.of());
        when(memberProfileRepository.findByAccountId(member.getId())).thenReturn(Optional.empty());

        ProductSkuLookupResponse res = productService.getBySku(moderator, SKU);

        assertEquals(1, res.getDaysIntoTerm());
        assertTrue(res.getPhotos().isEmpty());
        assertNull(res.getMemberCode());
        assertNull(res.getMemberFullName());
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
    @DisplayName("getSkuLabel: thành công -> trả byte PDF kèm tên file theo SKU")
    void getSkuLabel_success() {
        byte[] pdf = "%PDF-fake-label".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(productRepository.findById(SKU)).thenReturn(Optional.of(product));
        when(skuLabelPdfService.generate(product)).thenReturn(pdf);

        PdfDownload res = productService.getSkuLabel(moderator, SKU);

        assertEquals("sku-" + SKU + ".pdf", res.fileName());
        assertArrayEquals(pdf, res.content());
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

    // ==================== API 40: getInventory ====================

    private Pageable captureInventoryPageable(ProductStatus expectedStatus) {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findInventory(
                eq(warehouse.getId()), eq(expectedStatus), eq(false), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("getInventory: lọc theo kho của Moderator, map đúng field")
    void getInventory_success() {
        when(productRepository.findInventory(eq(warehouse.getId()), eq(ProductStatus.ON_SALE), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(product)));

        PageResponse<InventoryItemResponse> res =
                productService.getInventory(moderator, ProductStatus.ON_SALE, false, 0, 20);

        // Giả định PageResponse có getter getContent() (theo field "content" trong spec)
        assertEquals(1, res.getContent().size());
        InventoryItemResponse item = res.getContent().get(0);
        assertEquals(SKU, item.getId());
        assertEquals("Áo sơ mi Zara trắng", item.getName());
        assertEquals(ProductStatus.ON_SALE, item.getStatus());
    }

    @Test
    @DisplayName("getInventory: Moderator chưa gán kho -> MODERATOR_NO_WAREHOUSE")
    void getInventory_noWarehouse() {
        AppException ex = assertThrows(AppException.class,
                () -> productService.getInventory(moderatorOf(null), null, false, 0, 20));

        assertEquals(ErrorCode.MODERATOR_NO_WAREHOUSE, ex.getErrorCode());
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("getInventory: page âm -> 0, size < 1 -> mặc định 20")
    void getInventory_pagingDefaults() {
        when(productRepository.findInventory(any(), any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

        productService.getInventory(moderator, null, false, -5, 0);

        Pageable pageable = captureInventoryPageable(null);
        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
    }

    @Test
    @DisplayName("getInventory: size vượt trần -> bị chặn ở 50")
    void getInventory_sizeCapped() {
        when(productRepository.findInventory(any(), any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

        productService.getInventory(moderator, null, false, 2, 1000);

        Pageable pageable = captureInventoryPageable(null);
        assertEquals(2, pageable.getPageNumber());
        assertEquals(50, pageable.getPageSize());
    }

    @Test
    @DisplayName("getInventory: sắp xếp theo createdAt giảm dần")
    void getInventory_sortByCreatedAtDesc() {
        when(productRepository.findInventory(any(), any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

        productService.getInventory(moderator, null, false, 0, 20);

        Pageable pageable = captureInventoryPageable(null);
        assertNotNull(pageable.getSort().getOrderFor("createdAt"));
        assertTrue(pageable.getSort().getOrderFor("createdAt").isDescending());
    }
}