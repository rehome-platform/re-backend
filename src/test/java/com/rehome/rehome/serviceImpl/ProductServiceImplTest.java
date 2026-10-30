package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnShippingFeeCalculator;
import com.rehome.rehome.dto.request.CreateProductRequest;
import com.rehome.rehome.dto.request.PriceReductionRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.StockMovementType;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.entity.marketplace.Category;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPriceChange;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.StockMovementLog;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ProductMapper;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.service.SkuLabelPdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"unchecked", "rawtypes"})
class ProductServiceImplTest {

    @Mock ProductRepository productRepository;
    @Mock ProductPhotoRepository productPhotoRepository;
    @Mock ProductMapper productMapper;
    @Mock StockMovementLogRepository stockMovementLogRepository;
    @Mock SkuLabelPdfService skuLabelPdfService;

    // Trước đây thiếu @Mock nên bị null
    @Mock EndOfTermDecisionRepository endOfTermDecisionRepository;

    @Mock ConsignmentReceiptRepository receiptRepository;
    @Mock GhnShippingFeeCalculator ghnShippingFeeCalculator;

    // getMyProducts / getMyProduct dùng thêm hai repository này
    @Mock ProductPriceChangeRepository productPriceChangeRepository;
    @Mock OrderItemRepository orderItemRepository;

    @InjectMocks ProductServiceImpl service;

    private Account moderator;

    private final UUID memberId = UUID.randomUUID();
    private final Pageable pageable = PageRequest.of(0, 20);

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String PRODUCT_ID = "Q7-AK-000845";

    private Account owner;
    private Product product;

    @BeforeEach
    void setUp() {
        moderator = Account.builder()
                .id(UUID.randomUUID())
                .role(Role.MODERATOR)
                .build();

        stubRepositoryPage(Page.empty());
        owner = Account.builder()
                .id(UUID.randomUUID())
                .role(Role.MEMBER)
                .build();

        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .floorPrice(new BigDecimal("100000.00"))
                .build();

        product = Product.builder()
                .id(PRODUCT_ID)
                .member(owner)
                .consignmentReceipt(receipt)
                .currentPrice(new BigDecimal("300000.00"))
                .status(ProductStatus.ON_SALE)
                .termStartedAt(Instant.now())
                .build();
    }

    private PriceReductionRequest request(String newPrice) {
        PriceReductionRequest req = new PriceReductionRequest();
        req.setNewPrice(new BigDecimal(newPrice));
        return req;
    }

    private void stubFindProduct() {
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
    }

    private void assertError(ErrorCode expected, Runnable action) {
        AppException ex = assertThrows(AppException.class, action::run);
        assertEquals(expected, ex.getErrorCode());
        verify(productRepository, never()).save(any());
    }

    // ==================== API 23: reducePrice ====================

    @Test
    @DisplayName("Giảm giá thành công: cập nhật currentPrice và lastPriceReducedOn")
    void reducePrice_success() {
        stubFindProduct();

        PriceReductionResponse res = service.reducePrice(owner, PRODUCT_ID, request("200000"));

        assertEquals(0, new BigDecimal("200000.00").compareTo(res.getCurrentPrice()));
        assertEquals(0, new BigDecimal("200000.00").compareTo(product.getCurrentPrice()));
        assertEquals(LocalDate.now(VN_ZONE), product.getLastPriceReducedOn());
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("Giá mới bằng đúng giá sàn thì vẫn được phép")
    void reducePrice_equalToFloorPrice_success() {
        stubFindProduct();

        PriceReductionResponse res = service.reducePrice(owner, PRODUCT_ID, request("100000"));

        assertEquals(0, new BigDecimal("100000.00").compareTo(res.getCurrentPrice()));
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("Lần giảm gần nhất là hôm qua thì hôm nay giảm tiếp được")
    void reducePrice_lastReducedYesterday_success() {
        product.setLastPriceReducedOn(LocalDate.now(VN_ZONE).minusDays(1));
        stubFindProduct();

        service.reducePrice(owner, PRODUCT_ID, request("250000"));

        assertEquals(LocalDate.now(VN_ZONE), product.getLastPriceReducedOn());
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("Giá mới được làm tròn 2 chữ số thập phân (HALF_UP)")
    void reducePrice_roundsToTwoDecimals() {
        stubFindProduct();

        PriceReductionResponse res = service.reducePrice(owner, PRODUCT_ID, request("200000.555"));

        assertEquals(new BigDecimal("200000.56"), res.getCurrentPrice());
    }

    @Test
    @DisplayName("Không phải MEMBER -> NOT_MEMBER_ACCOUNT, không đụng DB")
    void reducePrice_notMember() {
        AppException ex = assertThrows(AppException.class,
                () -> service.reducePrice(moderator, PRODUCT_ID, request("200000")));

        assertEquals(ErrorCode.NOT_MEMBER_ACCOUNT, ex.getErrorCode());
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("Không tìm thấy sản phẩm -> PRODUCT_NOT_FOUND")
    void reducePrice_productNotFound() {
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.empty());

        assertError(ErrorCode.PRODUCT_NOT_FOUND,
                () -> service.reducePrice(owner, PRODUCT_ID, request("200000")));
    }

    @Test
    @DisplayName("Sản phẩm của Member khác -> PRODUCT_FORBIDDEN")
    void reducePrice_notOwner() {
        Account other = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
        stubFindProduct();

        assertError(ErrorCode.PRODUCT_FORBIDDEN,
                () -> service.reducePrice(other, PRODUCT_ID, request("200000")));
    }

    @Test
    @DisplayName("Sản phẩm không ON_SALE -> PRODUCT_NOT_ON_SALE")
    void reducePrice_notOnSale() {
        product.setStatus(ProductStatus.SOLD);
        stubFindProduct();

        assertError(ErrorCode.PRODUCT_NOT_ON_SALE,
                () -> service.reducePrice(owner, PRODUCT_ID, request("200000")));
    }

    @Test
    @DisplayName("Giá mới bằng giá hiện tại -> INVALID_NEW_PRICE")
    void reducePrice_equalToCurrentPrice() {
        stubFindProduct();

        assertError(ErrorCode.INVALID_NEW_PRICE,
                () -> service.reducePrice(owner, PRODUCT_ID, request("300000")));
    }

    @Test
    @DisplayName("Giá mới cao hơn giá hiện tại -> INVALID_NEW_PRICE")
    void reducePrice_higherThanCurrentPrice() {
        stubFindProduct();

        assertError(ErrorCode.INVALID_NEW_PRICE,
                () -> service.reducePrice(owner, PRODUCT_ID, request("350000")));
    }

    @Test
    @DisplayName("Giá mới thấp hơn giá sàn -> BELOW_FLOOR_PRICE")
    void reducePrice_belowFloorPrice() {
        stubFindProduct();

        assertError(ErrorCode.BELOW_FLOOR_PRICE,
                () -> service.reducePrice(owner, PRODUCT_ID, request("99999.99")));
    }

    @Test
    @DisplayName("Đã giảm giá hôm nay -> PRICE_CHANGE_LIMIT_REACHED")
    void reducePrice_alreadyReducedToday() {
        product.setLastPriceReducedOn(LocalDate.now(VN_ZONE));
        stubFindProduct();

        assertError(ErrorCode.PRICE_CHANGE_LIMIT_REACHED,
                () -> service.reducePrice(owner, PRODUCT_ID, request("200000")));
    }

    @Test
    @DisplayName("Giá hợp lệ nhưng đã giảm hôm nay: giá sản phẩm không bị đổi")
    void reducePrice_limitReached_priceUnchanged() {
        product.setLastPriceReducedOn(LocalDate.now(VN_ZONE));
        stubFindProduct();

        assertThrows(AppException.class,
                () -> service.reducePrice(owner, PRODUCT_ID, request("200000")));

        assertEquals(0, new BigDecimal("300000.00").compareTo(product.getCurrentPrice()));
    }

    // ==================== getProducts ====================

    private void stubRepositoryPage(Page<Product> page) {
        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class)))
                .thenReturn(page);
    }

    private Product product(String id, String name) {
        Product p = mock(Product.class, RETURNS_DEEP_STUBS);
        when(p.getId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        when(p.getBrand().getName()).thenReturn("Nike");
        when(p.getCategory().getName()).thenReturn("Áo");
        when(p.getSize()).thenReturn("M");
        when(p.getConditionNote()).thenReturn("Còn 90%");
        when(p.getConsignmentReceipt().getAgreedPrice()).thenReturn(new BigDecimal("100000"));
        when(p.getCurrentPrice()).thenReturn(new BigDecimal("60000"));
        return p;
    }

    private ProductPhoto photo(String productId, String url) {
        ProductPhoto ph = mock(ProductPhoto.class, RETURNS_DEEP_STUBS);
        when(ph.getProduct().getId()).thenReturn(productId);
        when(ph.getImageUrl()).thenReturn(url);
        return ph;
    }

    /** Gọi service với PageResponse.from bị mock, trả về Page đã map để kiểm tra nội dung. */
    private Page callAndCapture(BigDecimal min, BigDecimal max, int page, int size) {
        try (MockedStatic<PageResponse> mocked = mockStatic(PageResponse.class)) {
            mocked.when(() -> PageResponse.from(any(Page.class))).thenReturn(mock(PageResponse.class));

            service.getProducts("áo", null, null, min, max, null, page, size, null);

            ArgumentCaptor<Page> captor = ArgumentCaptor.forClass(Page.class);
            mocked.verify(() -> PageResponse.from(captor.capture()));
            return captor.getValue();
        }
    }

    @Test
    void getProducts_priceMinGreaterThanMax_throws() {
        assertThatThrownBy(() -> service.getProducts(null, null, null,
                new BigDecimal("200"), new BigDecimal("100"), null, 0, 10, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("priceMin không được lớn hơn priceMax.");

        verifyNoInteractions(productRepository, productPhotoRepository);
    }

    @Test
    void getProducts_mapsFieldsAndUsesFirstPhotoAsThumbnail() {
        Product p1 = product("p1", "Áo A");
        Product p2 = product("p2", "Áo B"); // không có ảnh
        stubRepositoryPage(new PageImpl<>(List.of(p1, p2)));
        ProductPhoto first = photo("p1", "first.jpg");
        ProductPhoto second = photo("p1", "second.jpg");
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(List.of("p1", "p2")))
                .thenReturn(List.of(first, second));

        Page mapped = callAndCapture(new BigDecimal("1"), new BigDecimal("2"), 2, 10);

        List<?> content = mapped.getContent();
        assertThat(content).hasSize(2);
        assertThat(content.get(0))
                .extracting("id", "name", "thumbnailUrl", "brandName", "categoryName",
                        "size", "condition", "originalPrice", "currentPrice")
                .containsExactly("p1", "Áo A", "first.jpg", "Nike", "Áo",
                        "M", "Còn 90%", new BigDecimal("100000"), new BigDecimal("60000"));
        assertThat(content.get(1)).extracting("id", "thumbnailUrl").containsExactly("p2", null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(ArgumentMatchers.<Specification<Product>>any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void getProducts_negativePageAndZeroSize_areClamped() {
        callAndCapture(null, null, -5, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(ArgumentMatchers.<Specification<Product>>any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void getProducts_onlyPriceMin_noValidationError() {
        Page mapped = callAndCapture(new BigDecimal("100"), null, 0, 10);
        assertThat(mapped.getContent()).isEmpty();
    }

    @Test
    void getProducts_onlyPriceMax_noValidationError() {
        Page mapped = callAndCapture(null, new BigDecimal("100"), 0, 10);
        assertThat(mapped.getContent()).isEmpty();
    }

    @Test
    void getProducts_priceMinEqualsMax_isAllowed() {
        Page mapped = callAndCapture(new BigDecimal("100"), new BigDecimal("100"), 0, 10);
        assertThat(mapped.getContent()).isEmpty();
    }

    // ==================== getProductDetail ====================

    @Test
    void getProductDetail_mapsAllFields() {
        UUID warehouseId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Instant inspectionDate = Instant.parse("2026-01-10T00:00:00Z");
        Product p = mock(Product.class, RETURNS_DEEP_STUBS);
        when(p.getId()).thenReturn("p1");
        when(p.getName()).thenReturn("Áo A");
        when(p.getCurrentPrice()).thenReturn(new BigDecimal("60000"));
        when(p.getConsignmentReceipt().getAgreedPrice()).thenReturn(new BigDecimal("100000"));
        when(p.getConsignmentReceipt().getConfirmedAt()).thenReturn(inspectionDate);
        when(p.getStatus()).thenReturn(ProductStatus.ON_SALE);
        when(p.getBrand().getName()).thenReturn("Nike");
        when(p.getCategory().getId()).thenReturn(categoryId);
        when(p.getCategory().getName()).thenReturn("Áo");
        when(p.getSize()).thenReturn("M");
        when(p.getConditionNote()).thenReturn("Còn 90%");
        when(p.getWarehouse().getId()).thenReturn(warehouseId);
        when(p.getWarehouse().getName()).thenReturn("Kho Q7");
        when(productRepository.findById("p1")).thenReturn(Optional.of(p));
        ProductPhoto photoA = photo("p1", "a.jpg");
        ProductPhoto photoB = photo("p1", "b.jpg");
        when(productPhotoRepository.findByProduct_IdOrderByDisplayOrderAscIdAsc("p1"))
                .thenReturn(List.of(photoA, photoB));

        ProductDetailResponse result = service.getProductDetail("p1", null);

        assertThat(result.getId()).isEqualTo("p1");
        assertThat(result.getName()).isEqualTo("Áo A");
        assertThat(result.getCurrentPrice()).isEqualTo(new BigDecimal("60000"));
        assertThat(result.getOriginalPrice()).isEqualTo(new BigDecimal("100000"));
        assertThat(result.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(result.getBrandName()).isEqualTo("Nike");
        assertThat(result.getCategoryId()).isEqualTo(categoryId);
        assertThat(result.getCategoryName()).isEqualTo("Áo");
        assertThat(result.getSize()).isEqualTo("M");
        assertThat(result.getCondition()).isEqualTo("Còn 90%");
        assertThat(result.getDescription()).isNull();
        assertThat(result.getInspectionDate()).isEqualTo(inspectionDate);
        assertThat(result.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(result.getWarehouseName()).isEqualTo("Kho Q7");
        assertThat(result.getPhotos()).containsExactly("a.jpg", "b.jpg");
    }

    // Sửa: service đã ném AppException(PRODUCT_NOT_FOUND) thay vì IllegalArgumentException
    @Test
    void getProductDetail_notFound_throwsAppException() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProductDetail("missing", null))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));

        verifyNoInteractions(productPhotoRepository);
    }

    // ==================== getMyProducts ====================

    private Product buildProduct(String id, ProductStatus status) {
        return Product.builder()
                .id(id)
                .name("Áo sơ mi Zara trắng")
                .status(status)
                .currentTerm((short) 1)
                .termStartedAt(Instant.parse("2026-09-13T08:10:00Z"))
                .endOfTermDeadline(Instant.parse("2026-10-13T08:10:00Z"))
                .build();
    }

    private MyProductResponse buildResponse(String id, ProductStatus status) {
        return MyProductResponse.builder()
                .id(id)
                .productName("Áo sơ mi Zara trắng")
                .status(status)
                .currentTerm((short) 1)
                .termStartedAt(Instant.parse("2026-09-13T08:10:00Z"))
                .endOfTermDeadline(Instant.parse("2026-10-13T08:10:00Z"))
                .build();
    }

    private ProductPhoto photoOf(Product product, String url) {
        ProductPhoto photo = mock(ProductPhoto.class);
        when(photo.getProduct()).thenReturn(product);
        when(photo.getImageUrl()).thenReturn(url);
        return photo;
    }

    @Test
    @DisplayName("getMyProducts: có status -> lọc theo status, ghép ảnh đầu và map đúng DTO")
    void getMyProducts_withStatus_returnsMappedPage() {
        Product product = buildProduct("Q7-AK-000845", ProductStatus.ON_SALE);
        MyProductResponse dto = buildResponse("Q7-AK-000845", ProductStatus.ON_SALE);
        Page<Product> repoPage = new PageImpl<>(List.of(product), pageable, 1);

        when(productRepository.findMine(memberId, ProductStatus.ON_SALE, false, pageable))
                .thenReturn(repoPage);

        // Tạo mock ảnh trước khi stub repository
        ProductPhoto photo = photoOf(product, "https://img/a.jpg");

        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(List.of("Q7-AK-000845")))
                .thenReturn(List.of(photo));

        when(endOfTermDecisionRepository.findByProductIds(List.of("Q7-AK-000845")))
                .thenReturn(List.of());

        when(orderItemRepository.findActiveByProductIds(List.of("Q7-AK-000845")))
                .thenReturn(List.of());

        when(productMapper.toMyProduct(product, "https://img/a.jpg", null, null))
                .thenReturn(dto);

        Page<MyProductResponse> result =
                service.getMyProducts(memberId, ProductStatus.ON_SALE, false, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
        assertEquals(0, result.getNumber());
        assertEquals(20, result.getSize());
        assertSame(dto, result.getContent().get(0));
        assertEquals("Q7-AK-000845", result.getContent().get(0).getId());
        assertEquals(ProductStatus.ON_SALE, result.getContent().get(0).getStatus());

        verify(productRepository, times(1))
                .findMine(memberId, ProductStatus.ON_SALE, false, pageable);

        verify(productMapper, times(1))
                .toMyProduct(product, "https://img/a.jpg", null, null);
    }

    @Test
    @DisplayName("getMyProducts: status = null -> truyền null xuống repository (lấy tất cả)")
    void getMyProducts_nullStatus_passesNullToRepository() {
        Product p1 = buildProduct("Q7-AK-000845", ProductStatus.ON_SALE);
        Product p2 = buildProduct("Q7-AK-000846", ProductStatus.WITHDRAWN);
        MyProductResponse d1 = buildResponse("Q7-AK-000845", ProductStatus.ON_SALE);
        MyProductResponse d2 = buildResponse("Q7-AK-000846", ProductStatus.WITHDRAWN);
        Page<Product> repoPage = new PageImpl<>(List.of(p1, p2), pageable, 2);

        when(productRepository.findMine(eq(memberId), isNull(), eq(false), eq(pageable)))
                .thenReturn(repoPage);
        when(productMapper.toMyProduct(eq(p1), any(), any(), any())).thenReturn(d1);
        when(productMapper.toMyProduct(eq(p2), any(), any(), any())).thenReturn(d2);

        Page<MyProductResponse> result = service.getMyProducts(memberId, null, false, pageable);

        assertEquals(2, result.getContent().size());
        assertEquals(d1, result.getContent().get(0));
        assertEquals(d2, result.getContent().get(1));
        verify(productRepository).findMine(memberId, null, false, pageable);
    }

    @Test
    @DisplayName("getMyProducts: pendingDecision = true -> truyền cờ này xuống repository (lọc hàng chờ quyết định cuối kỳ)")
    void getMyProducts_pendingDecision_passesFlagToRepository() {
        when(productRepository.findMine(memberId, null, true, pageable)).thenReturn(Page.empty());

        service.getMyProducts(memberId, null, true, pageable);

        verify(productRepository).findMine(memberId, null, true, pageable);
    }

    @Test
    @DisplayName("getMyProducts: không có sản phẩm -> trả page rỗng, không gọi mapper và không query dữ liệu phụ")
    void getMyProducts_noProducts_returnsEmptyPage() {
        Page<Product> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(productRepository.findMine(memberId, ProductStatus.WITHDRAWN, false, pageable))
                .thenReturn(emptyPage);

        Page<MyProductResponse> result =
                service.getMyProducts(memberId, ProductStatus.WITHDRAWN, false, pageable);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
        verifyNoInteractions(productMapper, productPhotoRepository, endOfTermDecisionRepository, orderItemRepository);
    }

    @Test
    @DisplayName("getMyProducts: giữ nguyên thông tin phân trang từ repository")
    void getMyProducts_keepsPagingInfo() {
        Pageable page1 = PageRequest.of(1, 1);
        Product product = buildProduct("Q7-AK-000846", ProductStatus.RESERVED);
        MyProductResponse dto = buildResponse("Q7-AK-000846", ProductStatus.RESERVED);
        Page<Product> repoPage = new PageImpl<>(List.of(product), page1, 3);

        when(productRepository.findMine(memberId, ProductStatus.RESERVED, false, page1))
                .thenReturn(repoPage);
        when(productMapper.toMyProduct(eq(product), any(), any(), any())).thenReturn(dto);

        Page<MyProductResponse> result =
                service.getMyProducts(memberId, ProductStatus.RESERVED, false, page1);

        assertEquals(1, result.getNumber());
        assertEquals(1, result.getSize());
        assertEquals(3, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
    }

    @Test
    @DisplayName("getMyProducts: sản phẩm có nhiều ảnh -> lấy ảnh đầu tiên (đã sắp theo displayOrder)")
    void getMyProducts_multiplePhotos_usesFirst() {
        Product product = buildProduct("Q7-AK-000845", ProductStatus.ON_SALE);

        when(productRepository.findMine(memberId, null, false, pageable))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 1));

        // Tạo hai ảnh trước khi stub repository
        ProductPhoto firstPhoto = photoOf(product, "first.jpg");
        ProductPhoto secondPhoto = photoOf(product, "second.jpg");

        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(List.of("Q7-AK-000845")))
                .thenReturn(List.of(firstPhoto, secondPhoto));

        service.getMyProducts(memberId, null, false, pageable);

        verify(productMapper).toMyProduct(product, "first.jpg", null, null);
    }

    @Test
    @DisplayName("getMyProducts: có quyết định cuối kỳ -> lấy bản ghi đầu (mới nhất) của sản phẩm")
    void getMyProducts_endOfTermDecision_usesFirstRow() {
        Product product = buildProduct("Q7-AK-000845", ProductStatus.ON_SALE);
        EndOfTermDecision newest = EndOfTermDecision.builder()
                .product(product).deadline(Instant.parse("2026-12-19T00:00:00Z")).build();
        EndOfTermDecision older = EndOfTermDecision.builder()
                .product(product).deadline(Instant.parse("2026-11-19T00:00:00Z")).build();
        when(productRepository.findMine(memberId, null, false, pageable))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 1));
        when(endOfTermDecisionRepository.findByProductIds(List.of("Q7-AK-000845")))
                .thenReturn(List.of(newest, older));

        service.getMyProducts(memberId, null, false, pageable);

        verify(productMapper).toMyProduct(product, null, newest, null);
    }

    @Test
    @DisplayName("getMyProducts: chỉ sản phẩm SOLD mới được gắn đơn hàng")
    void getMyProducts_orderAttachedOnlyWhenSold() {
        Product sold = buildProduct("Q7-AK-000845", ProductStatus.SOLD);
        Product onSale = buildProduct("Q7-AK-000846", ProductStatus.ON_SALE);
        Order order = Order.builder().id(UUID.randomUUID()).orderNo("OD-2026-000012").build();
        OrderItem soldItem = OrderItem.builder().product(sold).order(order).build();
        OrderItem onSaleItem = OrderItem.builder().product(onSale).order(order).build();

        List<String> ids = List.of("Q7-AK-000845", "Q7-AK-000846");
        when(productRepository.findMine(memberId, null, false, pageable))
                .thenReturn(new PageImpl<>(List.of(sold, onSale), pageable, 2));
        when(orderItemRepository.findActiveByProductIds(ids)).thenReturn(List.of(soldItem, onSaleItem));

        service.getMyProducts(memberId, null, false, pageable);

        verify(productMapper).toMyProduct(sold, null, null, order);
        verify(productMapper).toMyProduct(onSale, null, null, null);
    }

    // ==================== getMyProduct ====================

    private Product ownedProduct(ProductStatus status, short currentTerm, Instant termStartedAt) {
        BigDecimal agreed = new BigDecimal("450000");
        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .agreedPrice(agreed)
                .commissionRateTerm1(new BigDecimal("0.15"))
                .commissionRateTerm2(new BigDecimal("0.20"))
                .commissionRateTerm3(new BigDecimal("0.25"))
                .termLengthDays(30)
                .build();
        return Product.builder()
                .id("Q7-AK-000845")
                .name("Áo khoác Uniqlo")
                .member(owner)
                .consignmentReceipt(receipt)
                .status(status)
                .currentTerm(currentTerm)
                .termStartedAt(termStartedAt)
                .build();
    }

    @Test
    @DisplayName("getMyProduct: không tìm thấy -> PRODUCT_NOT_FOUND")
    void getMyProduct_notFound_throws() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.getMyProduct(owner.getId(), "missing"));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("getMyProduct: không phải chủ hàng -> PRODUCT_FORBIDDEN")
    void getMyProduct_notOwner_throwsForbidden() {
        Product p = ownedProduct(ProductStatus.ON_SALE, (short) 1, Instant.now());
        when(productRepository.findById("Q7-AK-000845")).thenReturn(Optional.of(p));

        AppException ex = assertThrows(AppException.class,
                () -> service.getMyProduct(UUID.randomUUID(), "Q7-AK-000845"));

        assertEquals(ErrorCode.PRODUCT_FORBIDDEN, ex.getErrorCode());
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("getMyProduct: chủ hàng -> ghép ảnh, quyết định cuối kỳ, lịch sử đổi giá và lịch sử kỳ vào mapper")
    void getMyProduct_success_passesAllPiecesToMapper() {
        Instant termStart = Instant.parse("2026-10-20T00:00:00Z");

        Product p = ownedProduct(ProductStatus.ON_SALE, (short) 1, termStart);

        ProductPriceChange change = ProductPriceChange.builder()
                .changedAt(Instant.parse("2026-10-25T09:00:00Z"))
                .oldPrice(new BigDecimal("450000"))
                .newPrice(new BigDecimal("400000"))
                .build();

        EndOfTermDecision decision = EndOfTermDecision.builder()
                .product(p)
                .deadline(Instant.parse("2026-12-19T00:00:00Z"))
                .build();

        MyProductResponse dto = buildResponse("Q7-AK-000845", ProductStatus.ON_SALE);

        when(productRepository.findById("Q7-AK-000845"))
                .thenReturn(Optional.of(p));

        when(productPriceChangeRepository.findByProduct_IdOrderByChangedAtAsc("Q7-AK-000845"))
                .thenReturn(List.of(change));

        // Tạo ảnh trước khi stub repository
        ProductPhoto photo = photoOf(p, "https://img/a.jpg");

        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(List.of("Q7-AK-000845")))
                .thenReturn(List.of(photo));

        when(endOfTermDecisionRepository.findByProductIds(List.of("Q7-AK-000845")))
                .thenReturn(List.of(decision));

        when(productMapper.toMyProductDetail(
                eq(p),
                eq("https://img/a.jpg"),
                eq(decision),
                isNull(),
                anyList(),
                anyList()
        )).thenReturn(dto);

        MyProductResponse result = service.getMyProduct(owner.getId(), "Q7-AK-000845");

        assertSame(dto, result);

        ArgumentCaptor<List> changes = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> terms = ArgumentCaptor.forClass(List.class);

        verify(productMapper).toMyProductDetail(
                eq(p),
                eq("https://img/a.jpg"),
                eq(decision),
                isNull(),
                changes.capture(),
                terms.capture()
        );

        assertEquals(1, changes.getValue().size());
        assertEquals(1, terms.getValue().size());
    }

    @Test
    @DisplayName("getMyProduct: sản phẩm SOLD -> lấy đơn đang hoạt động; không SOLD -> không tra đơn")
    void getMyProduct_orderOnlyWhenSold() {
        Product sold = ownedProduct(ProductStatus.SOLD, (short) 1, Instant.now());
        Order order = Order.builder().id(UUID.randomUUID()).orderNo("OD-2026-000012").build();
        when(productRepository.findById("Q7-AK-000845")).thenReturn(Optional.of(sold));
        when(orderItemRepository.findActiveByProductIds(List.of("Q7-AK-000845")))
                .thenReturn(List.of(OrderItem.builder().product(sold).order(order).build()));

        service.getMyProduct(owner.getId(), "Q7-AK-000845");

        verify(productMapper).toMyProductDetail(eq(sold), any(), any(), eq(order), anyList(), anyList());

        Product onSale = ownedProduct(ProductStatus.ON_SALE, (short) 1, Instant.now());
        when(productRepository.findById("Q7-AK-000845")).thenReturn(Optional.of(onSale));

        service.getMyProduct(owner.getId(), "Q7-AK-000845");

        verify(orderItemRepository, times(1)).findActiveByProductIds(anyList());
    }

    @Test
    @DisplayName("getMyProduct: kỳ 2 -> termHistory có 2 kỳ, giá mỗi kỳ là giá có hiệu lực lúc kỳ đó bắt đầu")
    void getMyProduct_termHistory_priceAtEachTermStart() {
        Instant termStart = Instant.parse("2026-11-19T00:00:00Z");   // kỳ 2 bắt đầu
        Product p = ownedProduct(ProductStatus.ON_SALE, (short) 2, termStart);
        BigDecimal agreed = p.getConsignmentReceipt().getAgreedPrice();
        BigDecimal reduced = new BigDecimal("400000");
        // đổi giá 10 ngày trước khi kỳ 2 bắt đầu = trong kỳ 1 (kỳ 1 bắt đầu cách kỳ 2 đúng 30 ngày)
        ProductPriceChange change = ProductPriceChange.builder()
                .changedAt(termStart.minus(Duration.ofDays(10)))
                .oldPrice(agreed).newPrice(reduced).build();
        when(productRepository.findById("Q7-AK-000845")).thenReturn(Optional.of(p));
        when(productPriceChangeRepository.findByProduct_IdOrderByChangedAtAsc("Q7-AK-000845"))
                .thenReturn(List.of(change));

        service.getMyProduct(owner.getId(), "Q7-AK-000845");

        ArgumentCaptor<List> terms = ArgumentCaptor.forClass(List.class);
        verify(productMapper).toMyProductDetail(eq(p), any(), any(), any(), anyList(), terms.capture());
        List<MyProductResponse.TermInfo> history = terms.getValue();
        assertEquals(2, history.size());
        assertThat(history).extracting("price").containsExactly(agreed, reduced);
    }

    // =====================================================================
    // create (API 37)
    // =====================================================================

    private final UUID receiptId = UUID.randomUUID();

    private ConsignmentReceipt receiptOf(Instant confirmedAt) {
        Account member = Account.builder().id(UUID.randomUUID()).build();
        Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).code("Q7").build();
        Category category = Category.builder().id(UUID.randomUUID()).code("AK").build();
        ConsignmentForm form = ConsignmentForm.builder()
                .id(UUID.randomUUID())
                .member(member)
                .warehouse(warehouse)
                .category(category)
                .brand(new Brand())
                .description("Mô tả Member tự khai")
                .build();

        return ConsignmentReceipt.builder()
                .id(receiptId)
                .receiptNo("KG-2026-000001")
                .consignmentForm(form)
                .member(member)
                .warehouse(warehouse)
                .conditionNote("Áo còn mới 90%, không lẹi")
                .agreedPrice(new BigDecimal("220000.00"))
                .floorPrice(new BigDecimal("110000.00"))
                .withdrawalFee(new BigDecimal("20000"))
                .confirmedAt(confirmedAt)
                .build();
    }

    private CreateProductRequest createRequest(List<String> photos) {
        CreateProductRequest r = new CreateProductRequest();
        r.setReceiptId(receiptId);
        r.setName("  Áo sơ mi Zara trắng  ");
        r.setSize("M");
        r.setShelfLocation("A1-03");
        r.setPhotoUrls(photos);
        return r;
    }

    /** Stub happy path; save() trả đúng đối tượng được truyền vào. */
    private void givenConfirmedReceipt(ConsignmentReceipt receipt) {
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
        when(productRepository.existsByConsignmentReceiptId(receiptId)).thenReturn(false);
        when(productRepository.nextSkuSequence()).thenReturn(845L);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static void assertCreateError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(expected));
    }

    @Test
    void create_success_buildsOnSaleProductFromReceipt() {
        ConsignmentReceipt receipt = receiptOf(Instant.now());
        givenConfirmedReceipt(receipt);
        Instant before = Instant.now();

        ProductResponse res = service.create(createRequest(List.of("https://cdn.rehome.vn/uploads/ao1.jpg")), moderator);

        Instant after = Instant.now();
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product p = captor.getValue();

        assertThat(res.getId()).isEqualTo("Q7-AK-000845");
        assertThat(res.getName()).isEqualTo("Áo sơ mi Zara trắng");   // đã trim
        assertThat(res.getStatus()).isEqualTo(ProductStatus.ON_SALE);

        assertThat(p.getId()).isEqualTo("Q7-AK-000845");
        assertThat(p.getConsignmentReceipt()).isSameAs(receipt);
        assertThat(p.getMember()).isSameAs(receipt.getMember());
        assertThat(p.getWarehouse()).isSameAs(receipt.getWarehouse());
        assertThat(p.getCategory()).isSameAs(receipt.getConsignmentForm().getCategory());
        assertThat(p.getBrand()).isSameAs(receipt.getConsignmentForm().getBrand());
        assertThat(p.getSize()).isEqualTo("M");
        assertThat(p.getShelfLocation()).isEqualTo("A1-03");
        assertThat(p.getCurrentPrice()).isEqualByComparingTo("220000");
        assertThat(p.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(p.getCurrentTerm()).isEqualTo((short) 1);

        // 30 ngày x (2 lần gia hạn + 1) = 90 ngày
        assertThat(p.getTermStartedAt()).isBetween(before, after);
        assertThat(p.getEndOfTermDeadline())
                .isBetween(before.plus(Duration.ofDays(90)), after.plus(Duration.ofDays(90)));
    }

    @Test
    void create_writesStockInLog() {
        givenConfirmedReceipt(receiptOf(Instant.now()));

        service.create(createRequest(null), moderator);

        ArgumentCaptor<StockMovementLog> captor = ArgumentCaptor.forClass(StockMovementLog.class);
        verify(stockMovementLogRepository).save(captor.capture());
        StockMovementLog log = captor.getValue();

        assertThat(log.getMovementType()).isEqualTo(StockMovementType.STOCK_IN);
        assertThat(log.getModerator()).isSameAs(moderator);
        assertThat(log.getProduct().getId()).isEqualTo("Q7-AK-000845");
        assertThat(log.getWarehouse()).isNotNull();
        assertThat(log.getToLocation()).isEqualTo("A1-03");
        assertThat(log.getFromLocation()).isNull();
    }

    @Test
    void create_conditionNoteComesFromReceipt_notFromMemberDescription() {
        givenConfirmedReceipt(receiptOf(Instant.now()));

        service.create(createRequest(null), moderator);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getConditionNote()).isEqualTo("Áo còn mới 90%, không lẹi");
    }

    @Test
    void create_endOfTermDeadline_usesReceiptSnapshot() {
        ConsignmentReceipt receipt = receiptOf(Instant.now());
        receipt.setTermLengthDays(60);
        receipt.setMaxRenewals(1);          // 60 x (1 + 1) = 120 ngày
        givenConfirmedReceipt(receipt);
        Instant before = Instant.now();

        service.create(createRequest(null), moderator);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getEndOfTermDeadline())
                .isAfterOrEqualTo(before.plus(Duration.ofDays(120)))
                .isBefore(before.plus(Duration.ofDays(121)));
    }

    @Test
    void create_photos_keepOrder_andAreNotBackgroundRemoved() {
        givenConfirmedReceipt(receiptOf(Instant.now()));

        service.create(createRequest(List.of("u0.jpg", "u1.jpg", "u2.jpg")), moderator);

        ArgumentCaptor<List<ProductPhoto>> captor = ArgumentCaptor.forClass(List.class);
        verify(productPhotoRepository).saveAll(captor.capture());
        List<ProductPhoto> photos = captor.getValue();

        assertThat(photos).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(photos.get(i).getImageUrl()).isEqualTo("u" + i + ".jpg");
            assertThat(photos.get(i).getDisplayOrder()).isEqualTo(i);          // 0 = ảnh đại diện
            assertThat(photos.get(i).getIsBackgroundRemoved()).isFalse();
        }
    }

    @Test
    void create_nullOrEmptyPhotos_doesNotSavePhotos() {
        givenConfirmedReceipt(receiptOf(Instant.now()));

        service.create(createRequest(null), moderator);
        service.create(createRequest(List.of()), moderator);

        verify(productPhotoRepository, never()).saveAll(any());
    }

    @Test
    void create_skuPadsSequenceToSixDigits() {
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receiptOf(Instant.now())));
        when(productRepository.existsByConsignmentReceiptId(receiptId)).thenReturn(false);
        when(productRepository.nextSkuSequence()).thenReturn(1L);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(createRequest(null), moderator).getId()).isEqualTo("Q7-AK-000001");
    }

    @Test
    void create_receiptNotFound_throws404() {
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.empty());

        assertCreateError(() -> service.create(createRequest(null), moderator), ErrorCode.RECEIPT_NOT_FOUND);

        verify(productRepository, never()).save(any());
        verifyNoInteractions(stockMovementLogRepository);
    }

    @Test
    void create_receiptNotConfirmed_throws409() {
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receiptOf(null)));

        assertCreateError(() -> service.create(createRequest(null), moderator), ErrorCode.RECEIPT_NOT_CONFIRMED);

        verify(productRepository, never()).save(any());
        verifyNoInteractions(stockMovementLogRepository);
    }

    @Test
    void create_receiptAlreadyHasProduct_throws409() {
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receiptOf(Instant.now())));
        when(productRepository.existsByConsignmentReceiptId(receiptId)).thenReturn(true);

        assertCreateError(() -> service.create(createRequest(null), moderator), ErrorCode.RECEIPT_ALREADY_HAS_PRODUCT);

        verify(productRepository, never()).save(any());
        verify(productPhotoRepository, never()).saveAll(any());
        verifyNoInteractions(stockMovementLogRepository);
    }

    // ---------- getShippingFeePreview ----------

    @Test
    void getShippingFeePreview_throwsNotFound_whenProductDoesNotExist() {
        when(productRepository.findById("P-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getShippingFeePreview("P-404", "Phường Bến Nghé", "TP.HCM"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy sản phẩm.");
    }

    @Test
    void getShippingFeePreview_throwsBadRequest_whenWardNameIsBlank() {
        Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).name("Kho Quận 7").build();
        Product product = Product.builder().id("P-001").warehouse(warehouse).build();
        when(productRepository.findById("P-001")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.getShippingFeePreview("P-001", "  ", "TP.HCM"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("wardName và provinceName là bắt buộc.");

        verifyNoInteractions(ghnShippingFeeCalculator);
    }

    @Test
    void getShippingFeePreview_returnsFeeFromCalculator_onSuccess() {
        Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).name("Kho Quận 7").build();
        Product product = Product.builder().id("P-001").warehouse(warehouse).build();
        when(productRepository.findById("P-001")).thenReturn(Optional.of(product));
        when(ghnShippingFeeCalculator.calculate(eq(warehouse), eq("Phường Bến Nghé"), eq("TP.HCM")))
                .thenReturn(new BigDecimal("19500"));

        ShippingFeePreviewResponse response = service.getShippingFeePreview("P-001", "Phường Bến Nghé", "TP.HCM");

        assertThat(response.getProductId()).isEqualTo("P-001");
        assertThat(response.getWarehouseId()).isEqualTo(warehouse.getId().toString());
        assertThat(response.getWarehouseName()).isEqualTo("Kho Quận 7");
        assertThat(response.getShippingFee()).isEqualTo(new BigDecimal("19500"));
    }
}