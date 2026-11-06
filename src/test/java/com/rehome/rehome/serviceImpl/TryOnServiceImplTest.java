package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateTryOnImageRequest;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.TryOnImageResponse;
import com.rehome.rehome.dto.response.TryOnSuggestionResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.TryOnModelType;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.enums.UploadFolder;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.entity.marketplace.Category;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.entity.tryon.TryOnImage;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.TryOnImageRepository;
import com.rehome.rehome.service.FileStorageService;
import com.rehome.rehome.service.TryOnImageGenerator;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TryOnServiceImplTest {

    private static final String IMAGE_URL = "http://localhost:8080/files/try-on/abc.png";

    private static final String TOP_ID = "Q7-AK-000852";
    private static final String BOTTOM_ID = "Q7-QU-000853";
    private static final String DRESS_ID = "Q7-DA-000854";

    private static final String TOP_PHOTO_URL = "http://localhost:8080/files/products/top.png";
    private static final String BOTTOM_PHOTO_URL = "http://localhost:8080/files/products/bottom.png";
    private static final String DRESS_PHOTO_URL = "http://localhost:8080/files/products/dress.png";

    private static final byte[] TOP_BYTES = {1, 1, 1};
    private static final byte[] BOTTOM_BYTES = {2, 2, 2};
    private static final byte[] DRESS_BYTES = {3, 3, 3};
    private static final byte[] RESULT_PNG = {9, 9, 9};

    @Mock private TryOnImageRepository tryOnImageRepository;
    @Mock private ProductPhotoRepository productPhotoRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private ProductRepository productRepository;
    @Mock private TryOnImageGenerator tryOnImageGenerator;

    @InjectMocks
    private TryOnServiceImpl tryOnService;

    private Account member;
    private Product top;
    private Product bottom;
    private Product dress;

    @BeforeEach
    void setUp() {
        member = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();

        top = Product.builder().id("Q7-AK-000001").name("Áo thun trắng")
                .currentPrice(new BigDecimal("150000")).status(ProductStatus.ON_SALE).build();
        bottom = Product.builder().id("Q7-QU-000002").name("Quần jeans")
                .currentPrice(new BigDecimal("250000")).status(ProductStatus.SOLD).build();
        dress = Product.builder().id("Q7-DA-000003").name("Đầm hoa")
                .currentPrice(new BigDecimal("300000")).status(ProductStatus.ON_SALE).build();
    }

    // ==================== helper dùng chung ====================

    private TryOnImage outfitImage() {
        return TryOnImage.builder()
                .id(UUID.randomUUID())
                .member(member)
                .modelType(TryOnModelType.FEMALE)
                .topProduct(top)
                .bottomProduct(bottom)
                .imageUrl(IMAGE_URL)
                .createdAt(Instant.parse("2026-10-30T10:00:00Z"))
                .build();
    }

    private ProductPhoto photo(Product p, String url) {
        return ProductPhoto.builder().product(p).imageUrl(url).displayOrder(0).build();
    }

    private ProductPhoto photo(Product p, String url, boolean backgroundRemoved) {
        return ProductPhoto.builder().product(p).imageUrl(url).displayOrder(0)
                .isBackgroundRemoved(backgroundRemoved).build();
    }

    // Category là entity nên mock: chỉ cần getTryonSlot(). lenient vì có test không chạm tới.
    private Category category(TryOnSlot slot) {
        Category c = mock(Category.class);
        lenient().when(c.getTryonSlot()).thenReturn(slot);
        return c;
    }

    private Product product(String id, TryOnSlot slot, ProductStatus status) {
        return Product.builder().id(id).name("SP " + id)
                .currentPrice(new BigDecimal("100000"))
                .status(status)
                .category(category(slot))
                .build();
    }

    // Món gợi ý đầy đủ trường như dữ liệu thật (API 96 trả cùng bộ trường với ProductSummaryResponse)
    private Product suggestedProduct(String id, UUID warehouseId) {
        Category c = mock(Category.class);
        lenient().when(c.getName()).thenReturn("Quần");
        return Product.builder().id(id).name("SP " + id)
                .brand(Brand.builder().name("Uniqlo").build())
                .category(c)
                .size("M")
                .conditionNote("Còn mới 90%")
                .consignmentReceipt(ConsignmentReceipt.builder().agreedPrice(new BigDecimal("200000")).build())
                .currentPrice(new BigDecimal("100000"))
                .warehouse(Warehouse.builder().id(warehouseId).build())
                .status(ProductStatus.ON_SALE)
                .build();
    }

    private CreateTryOnImageRequest request(TryOnModelType model, String topId, String bottomId, String dressId) {
        CreateTryOnImageRequest r = new CreateTryOnImageRequest();
        r.setModelType(model);
        r.setTopProductId(topId);
        r.setBottomProductId(bottomId);
        r.setDressProductId(dressId);
        return r;
    }

    // ==================== API 97: createImage ====================

    @Test
    @DisplayName("createImage: áo + quần hợp lệ -> sinh ảnh, lưu file + DB, trả đủ top/bottom")
    void createImage_outfit_success() {
        Product t = product(TOP_ID, TryOnSlot.TOP, ProductStatus.ON_SALE);
        Product b = product(BOTTOM_ID, TryOnSlot.BOTTOM, ProductStatus.ON_SALE);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(t, b));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(t, TOP_PHOTO_URL), photo(b, BOTTOM_PHOTO_URL)));
        when(fileStorageService.loadByUrl(TOP_PHOTO_URL)).thenReturn(Optional.of(TOP_BYTES));
        when(fileStorageService.loadByUrl(BOTTOM_PHOTO_URL)).thenReturn(Optional.of(BOTTOM_BYTES));
        when(tryOnImageGenerator.generateOutfit(TryOnModelType.FEMALE, TOP_BYTES, BOTTOM_BYTES))
                .thenReturn(RESULT_PNG);
        when(fileStorageService.store(anyString(), eq(RESULT_PNG), eq("image/png"))).thenReturn(IMAGE_URL);
        when(tryOnImageRepository.save(any(TryOnImage.class))).thenAnswer(inv -> inv.getArgument(0));

        TryOnImageResponse res = tryOnService.createImage(member,
                request(TryOnModelType.FEMALE, TOP_ID, BOTTOM_ID, null));

        assertEquals(TryOnModelType.FEMALE, res.getModelType());
        assertEquals(IMAGE_URL, res.getImageUrl());
        assertEquals(TOP_ID, res.getTop().getId());
        assertEquals(TOP_PHOTO_URL, res.getTop().getThumbnailUrl());
        assertEquals(BOTTOM_ID, res.getBottom().getId());
        assertEquals(BOTTOM_PHOTO_URL, res.getBottom().getThumbnailUrl());
        assertNull(res.getDress());

        ArgumentCaptor<TryOnImage> saved = ArgumentCaptor.forClass(TryOnImage.class);
        verify(tryOnImageRepository).save(saved.capture());
        assertSame(member, saved.getValue().getMember());
        assertEquals(TryOnModelType.FEMALE, saved.getValue().getModelType());
        assertSame(t, saved.getValue().getTopProduct());
        assertSame(b, saved.getValue().getBottomProduct());
        assertNull(saved.getValue().getDressProduct());
        assertEquals(IMAGE_URL, saved.getValue().getImageUrl());

        verify(tryOnImageGenerator, never()).generateDress(any(), any());
    }

    @Test
    @DisplayName("createImage: file lưu nằm trong thư mục TRY_ON và có đuôi .png")
    void createImage_storesFileUnderTryOnFolder() {
        Product t = product(TOP_ID, TryOnSlot.TOP, ProductStatus.ON_SALE);
        Product b = product(BOTTOM_ID, TryOnSlot.BOTTOM, ProductStatus.ON_SALE);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(t, b));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(t, TOP_PHOTO_URL), photo(b, BOTTOM_PHOTO_URL)));
        when(fileStorageService.loadByUrl(anyString())).thenReturn(Optional.of(TOP_BYTES));
        when(tryOnImageGenerator.generateOutfit(any(), any(), any())).thenReturn(RESULT_PNG);
        when(fileStorageService.store(anyString(), any(), anyString())).thenReturn(IMAGE_URL);
        when(tryOnImageRepository.save(any(TryOnImage.class))).thenAnswer(inv -> inv.getArgument(0));

        tryOnService.createImage(member, request(TryOnModelType.MALE, TOP_ID, BOTTOM_ID, null));

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).store(path.capture(), eq(RESULT_PNG), eq("image/png"));
        assertTrue(path.getValue().startsWith(UploadFolder.TRY_ON.getPath() + "/"));
        assertTrue(path.getValue().endsWith(".png"));
    }

    @Test
    @DisplayName("createImage: ID có khoảng trắng thừa -> được trim và vẫn tìm thấy sản phẩm")
    void createImage_trimsProductIds() {
        Product t = product(TOP_ID, TryOnSlot.TOP, ProductStatus.ON_SALE);
        Product b = product(BOTTOM_ID, TryOnSlot.BOTTOM, ProductStatus.ON_SALE);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(t, b));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(t, TOP_PHOTO_URL), photo(b, BOTTOM_PHOTO_URL)));
        when(fileStorageService.loadByUrl(anyString())).thenReturn(Optional.of(TOP_BYTES));
        when(tryOnImageGenerator.generateOutfit(any(), any(), any())).thenReturn(RESULT_PNG);
        when(fileStorageService.store(anyString(), any(), anyString())).thenReturn(IMAGE_URL);
        when(tryOnImageRepository.save(any(TryOnImage.class))).thenAnswer(inv -> inv.getArgument(0));

        TryOnImageResponse res = tryOnService.createImage(member,
                request(TryOnModelType.FEMALE, "  " + TOP_ID + " ", " " + BOTTOM_ID, null));

        assertEquals(TOP_ID, res.getTop().getId());
        assertEquals(BOTTOM_ID, res.getBottom().getId());
    }

    @Test
    @DisplayName("createImage: có ảnh đã tách nền -> ưu tiên ảnh đó khi gửi sang AI")
    void createImage_prefersBackgroundRemovedPhoto() {
        Product t = product(TOP_ID, TryOnSlot.TOP, ProductStatus.ON_SALE);
        Product b = product(BOTTOM_ID, TryOnSlot.BOTTOM, ProductStatus.ON_SALE);
        String topCutout = "http://localhost:8080/files/products/top-cutout.png";
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(t, b));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(
                        photo(t, TOP_PHOTO_URL, false),
                        photo(t, topCutout, true),
                        photo(b, BOTTOM_PHOTO_URL, false)));
        when(fileStorageService.loadByUrl(topCutout)).thenReturn(Optional.of(TOP_BYTES));
        when(fileStorageService.loadByUrl(BOTTOM_PHOTO_URL)).thenReturn(Optional.of(BOTTOM_BYTES));
        when(tryOnImageGenerator.generateOutfit(TryOnModelType.FEMALE, TOP_BYTES, BOTTOM_BYTES))
                .thenReturn(RESULT_PNG);
        when(fileStorageService.store(anyString(), any(), anyString())).thenReturn(IMAGE_URL);
        when(tryOnImageRepository.save(any(TryOnImage.class))).thenAnswer(inv -> inv.getArgument(0));

        tryOnService.createImage(member, request(TryOnModelType.FEMALE, TOP_ID, BOTTOM_ID, null));

        verify(fileStorageService).loadByUrl(topCutout);
        verify(fileStorageService, never()).loadByUrl(TOP_PHOTO_URL);
    }

    @Test
    @DisplayName("createImage: đầm + người mẫu nữ -> gọi generateDress, top/bottom null")
    void createImage_dress_success() {
        Product d = product(DRESS_ID, TryOnSlot.DRESS, ProductStatus.ON_SALE);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(d));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(d, DRESS_PHOTO_URL)));
        when(fileStorageService.loadByUrl(DRESS_PHOTO_URL)).thenReturn(Optional.of(DRESS_BYTES));
        when(tryOnImageGenerator.generateDress(TryOnModelType.FEMALE, DRESS_BYTES)).thenReturn(RESULT_PNG);
        when(fileStorageService.store(anyString(), eq(RESULT_PNG), eq("image/png"))).thenReturn(IMAGE_URL);
        when(tryOnImageRepository.save(any(TryOnImage.class))).thenAnswer(inv -> inv.getArgument(0));

        TryOnImageResponse res = tryOnService.createImage(member,
                request(TryOnModelType.FEMALE, null, null, DRESS_ID));

        assertNull(res.getTop());
        assertNull(res.getBottom());
        assertEquals(DRESS_ID, res.getDress().getId());
        assertEquals(DRESS_PHOTO_URL, res.getDress().getThumbnailUrl());
        verify(tryOnImageGenerator, never()).generateOutfit(any(), any(), any());
    }

    @Test
    @DisplayName("createImage: chỉ có áo (thiếu quần) -> INCOMPLETE_OUTFIT")
    void createImage_onlyTop_incomplete() {
        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, TOP_ID, null, null)));

        assertEquals(ErrorCode.INCOMPLETE_OUTFIT, ex.getErrorCode());
        verifyNoInteractions(productRepository, tryOnImageGenerator, fileStorageService);
    }

    @Test
    @DisplayName("createImage: chỉ có quần (thiếu áo) -> INCOMPLETE_OUTFIT")
    void createImage_onlyBottom_incomplete() {
        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, null, BOTTOM_ID, null)));

        assertEquals(ErrorCode.INCOMPLETE_OUTFIT, ex.getErrorCode());
        verifyNoInteractions(productRepository, tryOnImageGenerator, fileStorageService);
    }

    @Test
    @DisplayName("createImage: không gửi món nào -> INCOMPLETE_OUTFIT")
    void createImage_nothing_incomplete() {
        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, null, null, null)));

        assertEquals(ErrorCode.INCOMPLETE_OUTFIT, ex.getErrorCode());
        verifyNoInteractions(productRepository, tryOnImageGenerator, fileStorageService);
    }

    @Test
    @DisplayName("createImage: chuỗi rỗng/khoảng trắng coi như không có -> INCOMPLETE_OUTFIT")
    void createImage_blankIds_incomplete() {
        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, "  ", "", null)));

        assertEquals(ErrorCode.INCOMPLETE_OUTFIT, ex.getErrorCode());
        verifyNoInteractions(productRepository, tryOnImageGenerator);
    }

    @Test
    @DisplayName("createImage: lẫn cả áo + quần + đầm -> INCOMPLETE_OUTFIT")
    void createImage_mixedOutfitAndDress_incomplete() {
        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, TOP_ID, BOTTOM_ID, DRESS_ID)));

        assertEquals(ErrorCode.INCOMPLETE_OUTFIT, ex.getErrorCode());
        verifyNoInteractions(productRepository, tryOnImageGenerator);
    }

    @Test
    @DisplayName("createImage: đầm + người mẫu nam -> DRESS_NOT_SUPPORTED_FOR_MALE, chưa đụng DB/AI")
    void createImage_dressWithMale_rejected() {
        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.MALE, null, null, DRESS_ID)));

        assertEquals(ErrorCode.DRESS_NOT_SUPPORTED_FOR_MALE, ex.getErrorCode());
        verifyNoInteractions(productRepository, tryOnImageGenerator, fileStorageService);
    }

    @Test
    @DisplayName("createImage: một sản phẩm không tồn tại -> PRODUCT_NOT_FOUND")
    void createImage_productNotFound() {
        Product t = product(TOP_ID, TryOnSlot.TOP, ProductStatus.ON_SALE);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(t)); // thiếu quần

        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, TOP_ID, BOTTOM_ID, null)));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(tryOnImageGenerator);
        verify(tryOnImageRepository, never()).save(any());
    }

    @Test
    @DisplayName("createImage: áo nhưng danh mục thuộc slot BOTTOM -> TRYON_SLOT_MISMATCH")
    void createImage_slotMismatch() {
        Product wrongTop = product(TOP_ID, TryOnSlot.BOTTOM, ProductStatus.ON_SALE);
        Product b = product(BOTTOM_ID, TryOnSlot.BOTTOM, ProductStatus.ON_SALE);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(wrongTop, b));

        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, TOP_ID, BOTTOM_ID, null)));

        assertEquals(ErrorCode.TRYON_SLOT_MISMATCH, ex.getErrorCode());
        verifyNoInteractions(tryOnImageGenerator);
    }

    @Test
    @DisplayName("createImage: sản phẩm không có danh mục -> TRYON_SLOT_MISMATCH")
    void createImage_nullCategory_slotMismatch() {
        Product noCategory = Product.builder().id(TOP_ID).name("Không danh mục")
                .status(ProductStatus.ON_SALE).build();
        Product b = product(BOTTOM_ID, TryOnSlot.BOTTOM, ProductStatus.ON_SALE);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(noCategory, b));

        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, TOP_ID, BOTTOM_ID, null)));

        assertEquals(ErrorCode.TRYON_SLOT_MISMATCH, ex.getErrorCode());
    }

    @Test
    @DisplayName("createImage: dùng cùng một sản phẩm cho cả áo và quần -> TRYON_SLOT_MISMATCH")
    void createImage_sameProductForTopAndBottom() {
        Product t = product(TOP_ID, TryOnSlot.TOP, ProductStatus.ON_SALE);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(t));

        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, TOP_ID, TOP_ID, null)));

        assertEquals(ErrorCode.TRYON_SLOT_MISMATCH, ex.getErrorCode());
        verifyNoInteractions(tryOnImageGenerator);
    }

    @Test
    @DisplayName("createImage: sản phẩm không còn ON_SALE -> TRYON_PRODUCT_NOT_AVAILABLE")
    void createImage_productNotOnSale() {
        Product t = product(TOP_ID, TryOnSlot.TOP, ProductStatus.ON_SALE);
        Product soldBottom = product(BOTTOM_ID, TryOnSlot.BOTTOM, ProductStatus.SOLD);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(t, soldBottom));

        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, TOP_ID, BOTTOM_ID, null)));

        assertEquals(ErrorCode.TRYON_PRODUCT_NOT_AVAILABLE, ex.getErrorCode());
        verifyNoInteractions(tryOnImageGenerator);
    }

    @Test
    @DisplayName("createImage: sản phẩm không có ảnh nào -> TRYON_PRODUCT_NOT_AVAILABLE, không gọi AI")
    void createImage_missingPhoto() {
        Product t = product(TOP_ID, TryOnSlot.TOP, ProductStatus.ON_SALE);
        Product b = product(BOTTOM_ID, TryOnSlot.BOTTOM, ProductStatus.ON_SALE);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(t, b));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(b, BOTTOM_PHOTO_URL))); // áo không có ảnh
        lenient().when(fileStorageService.loadByUrl(anyString())).thenReturn(Optional.of(BOTTOM_BYTES));

        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, TOP_ID, BOTTOM_ID, null)));

        assertEquals(ErrorCode.TRYON_PRODUCT_NOT_AVAILABLE, ex.getErrorCode());
        verifyNoInteractions(tryOnImageGenerator);
    }

    @Test
    @DisplayName("createImage: file ảnh sản phẩm không còn trong storage -> TRYON_PRODUCT_NOT_AVAILABLE")
    void createImage_photoFileGone() {
        Product t = product(TOP_ID, TryOnSlot.TOP, ProductStatus.ON_SALE);
        Product b = product(BOTTOM_ID, TryOnSlot.BOTTOM, ProductStatus.ON_SALE);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(t, b));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(t, TOP_PHOTO_URL), photo(b, BOTTOM_PHOTO_URL)));
        when(fileStorageService.loadByUrl(TOP_PHOTO_URL)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, TOP_ID, BOTTOM_ID, null)));

        assertEquals(ErrorCode.TRYON_PRODUCT_NOT_AVAILABLE, ex.getErrorCode());
        verifyNoInteractions(tryOnImageGenerator);
    }

    @Test
    @DisplayName("createImage: AI lỗi/timeout -> ném lỗi 502, KHÔNG lưu file, KHÔNG ghi DB")
    void createImage_generatorFails_nothingSaved() {
        Product t = product(TOP_ID, TryOnSlot.TOP, ProductStatus.ON_SALE);
        Product b = product(BOTTOM_ID, TryOnSlot.BOTTOM, ProductStatus.ON_SALE);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(t, b));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(t, TOP_PHOTO_URL), photo(b, BOTTOM_PHOTO_URL)));
        when(fileStorageService.loadByUrl(TOP_PHOTO_URL)).thenReturn(Optional.of(TOP_BYTES));
        when(fileStorageService.loadByUrl(BOTTOM_PHOTO_URL)).thenReturn(Optional.of(BOTTOM_BYTES));
        when(tryOnImageGenerator.generateOutfit(any(), any(), any()))
                .thenThrow(new AppException(ErrorCode.TRYON_GENERATION_FAILED));

        AppException ex = assertThrows(AppException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, TOP_ID, BOTTOM_ID, null)));

        assertEquals(ErrorCode.TRYON_GENERATION_FAILED, ex.getErrorCode());
        verify(fileStorageService, never()).store(anyString(), any(), anyString());
        verify(tryOnImageRepository, never()).save(any());
    }

    @Test
    @DisplayName("createImage: ghi DB lỗi -> xóa file vừa lưu (không để file mồ côi) rồi ném lại lỗi")
    void createImage_dbSaveFails_removesStoredFile() {
        Product t = product(TOP_ID, TryOnSlot.TOP, ProductStatus.ON_SALE);
        Product b = product(BOTTOM_ID, TryOnSlot.BOTTOM, ProductStatus.ON_SALE);
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(t, b));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(t, TOP_PHOTO_URL), photo(b, BOTTOM_PHOTO_URL)));
        when(fileStorageService.loadByUrl(anyString())).thenReturn(Optional.of(TOP_BYTES));
        when(tryOnImageGenerator.generateOutfit(any(), any(), any())).thenReturn(RESULT_PNG);
        when(fileStorageService.store(anyString(), any(), anyString())).thenReturn(IMAGE_URL);
        when(tryOnImageRepository.save(any(TryOnImage.class))).thenThrow(new RuntimeException("db down"));
        when(fileStorageService.deleteByUrl(eq(IMAGE_URL), anyString())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                tryOnService.createImage(member, request(TryOnModelType.FEMALE, TOP_ID, BOTTOM_ID, null)));

        assertEquals("db down", ex.getMessage());
        verify(fileStorageService).deleteByUrl(eq(IMAGE_URL), eq("image/png"));
    }

    // ==================== F-080: getCollection ====================

    @Test
    @DisplayName("getCollection: map đúng ảnh, người mẫu và thông tin áo + quần")
    void getCollection_success() {
        TryOnImage image = outfitImage();
        when(tryOnImageRepository.findByMember_Id(eq(member.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(image)));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(top, "https://img/top.jpg"), photo(bottom, "https://img/bottom.jpg")));

        PageResponse<TryOnImageResponse> res = tryOnService.getCollection(member, 0, 20);

        assertEquals(1, res.getContent().size());
        TryOnImageResponse r = res.getContent().get(0);
        assertEquals(image.getId(), r.getId());
        assertEquals(TryOnModelType.FEMALE, r.getModelType());
        assertEquals(IMAGE_URL, r.getImageUrl());
        assertEquals(image.getCreatedAt(), r.getCreatedAt());

        assertEquals("Q7-AK-000001", r.getTop().getId());
        assertEquals("Áo thun trắng", r.getTop().getName());
        assertEquals("https://img/top.jpg", r.getTop().getThumbnailUrl());
        assertEquals(new BigDecimal("150000"), r.getTop().getCurrentPrice());
        assertEquals(ProductStatus.ON_SALE, r.getTop().getStatus());

        assertEquals("Q7-QU-000002", r.getBottom().getId());
        assertEquals(ProductStatus.SOLD, r.getBottom().getStatus());
        assertNull(r.getDress());
    }

    @Test
    @DisplayName("getCollection: ảnh thử Đầm -> top/bottom null, dress có giá trị")
    void getCollection_dressImage() {
        TryOnImage image = TryOnImage.builder()
                .id(UUID.randomUUID()).member(member).modelType(TryOnModelType.MALE)
                .dressProduct(dress).imageUrl(IMAGE_URL).build();
        when(tryOnImageRepository.findByMember_Id(eq(member.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(image)));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(dress, "https://img/dress.jpg")));

        TryOnImageResponse r = tryOnService.getCollection(member, 0, 20).getContent().get(0);

        assertNull(r.getTop());
        assertNull(r.getBottom());
        assertEquals("Q7-DA-000003", r.getDress().getId());
        assertEquals("https://img/dress.jpg", r.getDress().getThumbnailUrl());
    }

    @Test
    @DisplayName("getCollection: món không có ảnh -> thumbnailUrl null, không lỗi")
    void getCollection_productWithoutPhoto() {
        when(tryOnImageRepository.findByMember_Id(eq(member.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(outfitImage())));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of());

        TryOnImageResponse r = tryOnService.getCollection(member, 0, 20).getContent().get(0);

        assertNull(r.getTop().getThumbnailUrl());
        assertNull(r.getBottom().getThumbnailUrl());
    }

    @Test
    @DisplayName("getCollection: bộ sưu tập rỗng -> danh sách rỗng, không query ảnh sản phẩm")
    void getCollection_empty() {
        when(tryOnImageRepository.findByMember_Id(eq(member.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<TryOnImageResponse> res = tryOnService.getCollection(member, 0, 20);

        assertTrue(res.getContent().isEmpty());
        verifyNoInteractions(productPhotoRepository);
    }

    @Test
    @DisplayName("getCollection: page âm -> 0, size < 1 -> 20, sắp theo createdAt giảm dần")
    void getCollection_pagingDefaults() {
        when(tryOnImageRepository.findByMember_Id(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        tryOnService.getCollection(member, -3, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(tryOnImageRepository).findByMember_Id(eq(member.getId()), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertNotNull(pageable.getSort().getOrderFor("createdAt"));
        assertTrue(pageable.getSort().getOrderFor("createdAt").isDescending());
    }

    @Test
    @DisplayName("getCollection: size vượt trần -> bị chặn ở 50")
    void getCollection_sizeCapped() {
        when(tryOnImageRepository.findByMember_Id(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        tryOnService.getCollection(member, 2, 1000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(tryOnImageRepository).findByMember_Id(eq(member.getId()), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(50, captor.getValue().getPageSize());
    }

    // ==================== ProductInfo.warehouseId ====================

    @Test
    @DisplayName("getCollection: ProductInfo trả warehouseId để FE biết mua cả bộ có tách đơn không")
    void getCollection_returnsWarehouseId() {
        UUID topWarehouse = UUID.randomUUID();
        UUID bottomWarehouse = UUID.randomUUID();
        top.setWarehouse(Warehouse.builder().id(topWarehouse).build());
        bottom.setWarehouse(Warehouse.builder().id(bottomWarehouse).build());
        when(tryOnImageRepository.findByMember_Id(eq(member.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(outfitImage())));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of());

        TryOnImageResponse r = tryOnService.getCollection(member, 0, 20).getContent().get(0);

        assertEquals(topWarehouse, r.getTop().getWarehouseId());
        assertEquals(bottomWarehouse, r.getBottom().getWarehouseId());
    }

    // ==================== GET /tryon/images/{id}: getImage ====================

    @Test
    @DisplayName("getImage: chủ ảnh -> trả imageUrl đã lưu và thông tin từng món")
    void getImage_success() {
        UUID topWarehouse = UUID.randomUUID();
        top.setWarehouse(Warehouse.builder().id(topWarehouse).build());
        TryOnImage image = outfitImage();
        when(tryOnImageRepository.findWithProductsById(image.getId())).thenReturn(Optional.of(image));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(top, "https://img/top.jpg")));

        TryOnImageResponse r = tryOnService.getImage(member, image.getId());

        assertEquals(image.getId(), r.getId());
        assertEquals(IMAGE_URL, r.getImageUrl());
        assertEquals(TryOnModelType.FEMALE, r.getModelType());
        assertEquals("Q7-AK-000001", r.getTop().getId());
        assertEquals("https://img/top.jpg", r.getTop().getThumbnailUrl());
        assertEquals(topWarehouse, r.getTop().getWarehouseId());
        assertEquals("Q7-QU-000002", r.getBottom().getId());
        assertNull(r.getDress());
    }

    @Test
    @DisplayName("getImage: không có ảnh -> TRYON_IMAGE_NOT_FOUND")
    void getImage_notFound() {
        UUID id = UUID.randomUUID();
        when(tryOnImageRepository.findWithProductsById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> tryOnService.getImage(member, id));

        assertEquals(ErrorCode.TRYON_IMAGE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("getImage: ảnh của Member khác -> TRYON_IMAGE_NOT_FOUND (không lộ ảnh tồn tại)")
    void getImage_notOwner() {
        TryOnImage image = outfitImage();
        Account other = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
        when(tryOnImageRepository.findWithProductsById(image.getId())).thenReturn(Optional.of(image));

        AppException ex = assertThrows(AppException.class, () -> tryOnService.getImage(other, image.getId()));

        assertEquals(ErrorCode.TRYON_IMAGE_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(productPhotoRepository);
    }

    // ==================== API 96: getSuggestions ====================

    @Test
    @DisplayName("getSuggestions: trả đủ trường như ProductSummaryResponse + warehouseId, ưu tiên kho của món đang xem, tối đa 10 món")
    void getSuggestions_success() {
        UUID warehouseId = UUID.randomUUID();
        Product suggested = suggestedProduct("Q7-QU-000900", warehouseId);
        when(productRepository.findWarehouseIdById(TOP_ID)).thenReturn(Optional.of(warehouseId));
        when(productRepository.findTryOnSuggestions(eq(TryOnSlot.BOTTOM), eq(TOP_ID), eq(warehouseId), any(Pageable.class)))
                .thenReturn(List.of(suggested));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(suggested, "http://localhost:8080/files/products/s900.png")));

        List<TryOnSuggestionResponse> res = tryOnService.getSuggestions("BOTTOM", TOP_ID);

        assertEquals(1, res.size());
        TryOnSuggestionResponse r = res.get(0);
        assertEquals("Q7-QU-000900", r.getId());
        assertEquals("SP Q7-QU-000900", r.getName());
        assertEquals("http://localhost:8080/files/products/s900.png", r.getThumbnailUrl());
        assertEquals("Uniqlo", r.getBrandName());
        assertEquals("Quần", r.getCategoryName());
        assertEquals("M", r.getSize());
        assertEquals("Còn mới 90%", r.getCondition());
        assertEquals(new BigDecimal("200000"), r.getOriginalPrice());
        assertEquals(new BigDecimal("100000"), r.getCurrentPrice());
        assertEquals(warehouseId, r.getWarehouseId());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findTryOnSuggestions(any(), any(), any(), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
    }

    @Test
    @DisplayName("getSuggestions: nhiều món -> thumbnail lấy bằng 1 query, món không có ảnh thì thumbnailUrl null")
    void getSuggestions_thumbnailsLoadedInOneQuery() {
        UUID warehouseId = UUID.randomUUID();
        Product withPhoto = suggestedProduct("Q7-QU-000901", warehouseId);
        Product withoutPhoto = suggestedProduct("Q7-QU-000902", warehouseId);
        when(productRepository.findTryOnSuggestions(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(withPhoto, withoutPhoto));
        // Hai ảnh của cùng một món: lấy ảnh đầu tiên (displayOrder nhỏ nhất)
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(
                        photo(withPhoto, "http://localhost:8080/files/products/first.png"),
                        photo(withPhoto, "http://localhost:8080/files/products/second.png")));

        List<TryOnSuggestionResponse> res = tryOnService.getSuggestions("TOP", null);

        assertEquals(2, res.size());
        assertEquals("http://localhost:8080/files/products/first.png", res.get(0).getThumbnailUrl());
        assertNull(res.get(1).getThumbnailUrl());
        verify(productPhotoRepository, times(1)).findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection());
    }

    @Test
    @DisplayName("getSuggestions: không có món nào -> trả danh sách rỗng, không query ảnh")
    void getSuggestions_empty_noPhotoQuery() {
        when(productRepository.findTryOnSuggestions(any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

        assertTrue(tryOnService.getSuggestions("TOP", null).isEmpty());
        verifyNoInteractions(productPhotoRepository);
    }

    @Test
    @DisplayName("getSuggestions: slot không phân biệt hoa thường và bỏ khoảng trắng")
    void getSuggestions_slotIsCaseInsensitive() {
        when(productRepository.findTryOnSuggestions(eq(TryOnSlot.TOP), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        assertTrue(tryOnService.getSuggestions(" top ", null).isEmpty());
        verify(productRepository).findTryOnSuggestions(eq(TryOnSlot.TOP), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("getSuggestions: không có excludeProductId -> không loại món nào, không ưu tiên kho")
    void getSuggestions_withoutExcludeId() {
        when(productRepository.findTryOnSuggestions(any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

        tryOnService.getSuggestions("TOP", null);

        verify(productRepository, never()).findWarehouseIdById(any());
        verify(productRepository).findTryOnSuggestions(eq(TryOnSlot.TOP), eq(""),
                eq(new UUID(0L, 0L)), any(Pageable.class));
    }

    @Test
    @DisplayName("getSuggestions: excludeProductId không tồn tại -> vẫn trả gợi ý, không ưu tiên kho")
    void getSuggestions_unknownExcludeId() {
        when(productRepository.findWarehouseIdById("NOPE")).thenReturn(Optional.empty());
        when(productRepository.findTryOnSuggestions(any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

        tryOnService.getSuggestions("BOTTOM", "NOPE");

        verify(productRepository).findTryOnSuggestions(eq(TryOnSlot.BOTTOM), eq("NOPE"),
                eq(new UUID(0L, 0L)), any(Pageable.class));
    }

    @Test
    @DisplayName("getSuggestions: slot sai/thiếu/DRESS -> INVALID_SUGGESTION_SLOT, không query")
    void getSuggestions_invalidSlot() {
        for (String slot : new String[]{null, "", "  ", "ABC", "DRESS"}) {
            AppException ex = assertThrows(AppException.class, () -> tryOnService.getSuggestions(slot, TOP_ID));
            assertEquals(ErrorCode.INVALID_SUGGESTION_SLOT, ex.getErrorCode());
        }
        verifyNoInteractions(productRepository);
    }

    // ==================== F-081: deleteImage ====================

    @Test
    @DisplayName("deleteImage: chủ ảnh -> xóa dòng DB rồi xóa file")
    void deleteImage_success() {
        TryOnImage image = outfitImage();
        when(tryOnImageRepository.findById(image.getId())).thenReturn(Optional.of(image));
        when(fileStorageService.deleteByUrl(eq(IMAGE_URL), anyString())).thenReturn(true);

        tryOnService.deleteImage(member, image.getId());

        var order = inOrder(tryOnImageRepository, fileStorageService);
        order.verify(tryOnImageRepository).delete(image);
        order.verify(tryOnImageRepository).flush();
        order.verify(fileStorageService).deleteByUrl(eq(IMAGE_URL), eq("image/png"));
    }

    @Test
    @DisplayName("deleteImage: không có ảnh -> TRYON_IMAGE_NOT_FOUND, không xóa gì")
    void deleteImage_notFound() {
        UUID id = UUID.randomUUID();
        when(tryOnImageRepository.findById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> tryOnService.deleteImage(member, id));

        assertEquals(ErrorCode.TRYON_IMAGE_NOT_FOUND, ex.getErrorCode());
        verify(tryOnImageRepository, never()).delete(any());
        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("deleteImage: ảnh của Member khác -> TRYON_IMAGE_NOT_FOUND (không lộ ảnh tồn tại), không xóa DB/file")
    void deleteImage_notOwner() {
        TryOnImage image = outfitImage();
        Account other = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
        when(tryOnImageRepository.findById(image.getId())).thenReturn(Optional.of(image));

        AppException ex = assertThrows(AppException.class, () -> tryOnService.deleteImage(other, image.getId()));

        assertEquals(ErrorCode.TRYON_IMAGE_NOT_FOUND, ex.getErrorCode());
        verify(tryOnImageRepository, never()).delete(any());
        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("deleteImage: storage báo không thấy file -> vẫn xóa dòng DB thành công")
    void deleteImage_fileAlreadyGone() {
        TryOnImage image = outfitImage();
        when(tryOnImageRepository.findById(image.getId())).thenReturn(Optional.of(image));
        when(fileStorageService.deleteByUrl(eq(IMAGE_URL), anyString())).thenReturn(false);

        assertDoesNotThrow(() -> tryOnService.deleteImage(member, image.getId()));

        verify(tryOnImageRepository).delete(image);
    }

    @Test
    @DisplayName("deleteImage: storage ném lỗi khi xóa file -> không làm hỏng việc xóa dòng DB")
    void deleteImage_storageFailureIsSwallowed() {
        TryOnImage image = outfitImage();
        when(tryOnImageRepository.findById(image.getId())).thenReturn(Optional.of(image));
        when(fileStorageService.deleteByUrl(eq(IMAGE_URL), anyString()))
                .thenThrow(new AppException(ErrorCode.FILE_DELETE_FAILED));

        assertDoesNotThrow(() -> tryOnService.deleteImage(member, image.getId()));

        verify(tryOnImageRepository).delete(image);
    }
}