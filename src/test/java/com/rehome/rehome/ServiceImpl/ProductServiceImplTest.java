package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ProductDetailResponse;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"unchecked", "rawtypes"})
class ProductServiceImplTest {

    @Mock ProductRepository productRepository;
    @Mock ProductPhotoRepository productPhotoRepository;
    @InjectMocks ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        stubRepositoryPage(Page.empty());
    }

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

            service.getProducts("áo", null, null, min, max, null, page, size);

            ArgumentCaptor<Page> captor = ArgumentCaptor.forClass(Page.class);
            mocked.verify(() -> PageResponse.from(captor.capture()));
            return captor.getValue();
        }
    }

    @Test
    void getProducts_priceMinGreaterThanMax_throws() {
        assertThatThrownBy(() -> service.getProducts(null, null, null,
                new BigDecimal("200"), new BigDecimal("100"), null, 0, 10))
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
        when(productPhotoRepository.findByProduct_IdInOrderByIdAsc(List.of("p1", "p2")))
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

    @Test
    void getProductDetail_mapsAllFields() {
        UUID warehouseId = UUID.randomUUID();
        Instant inspectionDate = Instant.parse("2026-01-10T00:00:00Z");
        Product p = mock(Product.class, RETURNS_DEEP_STUBS);
        when(p.getId()).thenReturn("p1");
        when(p.getName()).thenReturn("Áo A");
        when(p.getCurrentPrice()).thenReturn(new BigDecimal("60000"));
        when(p.getConsignmentReceipt().getAgreedPrice()).thenReturn(new BigDecimal("100000"));
        when(p.getConsignmentReceipt().getConfirmedAt()).thenReturn(inspectionDate);
        when(p.getStatus()).thenReturn(ProductStatus.ON_SALE);
        when(p.getBrand().getName()).thenReturn("Nike");
        when(p.getCategory().getName()).thenReturn("Áo");
        when(p.getSize()).thenReturn("M");
        when(p.getConditionNote()).thenReturn("Còn 90%");
        when(p.getWarehouse().getId()).thenReturn(warehouseId);
        when(p.getWarehouse().getName()).thenReturn("Kho Q7");
        when(productRepository.findById("p1")).thenReturn(Optional.of(p));
        ProductPhoto photoA = photo("p1", "a.jpg");
        ProductPhoto photoB = photo("p1", "b.jpg");
        when(productPhotoRepository.findByProduct_IdOrderByIdAsc("p1"))
                .thenReturn(List.of(photoA, photoB));

        ProductDetailResponse result = service.getProductDetail("p1");

        assertThat(result.getId()).isEqualTo("p1");
        assertThat(result.getName()).isEqualTo("Áo A");
        assertThat(result.getCurrentPrice()).isEqualTo(new BigDecimal("60000"));
        assertThat(result.getOriginalPrice()).isEqualTo(new BigDecimal("100000"));
        assertThat(result.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(result.getBrandName()).isEqualTo("Nike");
        assertThat(result.getCategoryName()).isEqualTo("Áo");
        assertThat(result.getSize()).isEqualTo("M");
        assertThat(result.getCondition()).isEqualTo("Còn 90%");
        assertThat(result.getDescription()).isEqualTo("Còn 90%");
        assertThat(result.getInspectionDate()).isEqualTo(inspectionDate);
        assertThat(result.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(result.getWarehouseName()).isEqualTo("Kho Q7");
        assertThat(result.getPhotos()).containsExactly("a.jpg", "b.jpg");
    }

    @Test
    void getProductDetail_notFound_throws() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProductDetail("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy sản phẩm.");

        verifyNoInteractions(productPhotoRepository);
    }
}