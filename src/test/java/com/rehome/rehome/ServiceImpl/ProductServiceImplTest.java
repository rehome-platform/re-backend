package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ProductSummaryResponse;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.entity.marketplace.Category;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock ProductRepository productRepository;
    @Mock ProductPhotoRepository productPhotoRepository;

    @InjectMocks
    ProductServiceImpl productService;

    Category category;
    Brand brand;
    ConsignmentReceipt receipt;
    Product product;

    @BeforeEach
    void setup() {
        category = Category.builder().name("Áo sơ mi").build();
        brand = Brand.builder().name("Zara").build();
        receipt = ConsignmentReceipt.builder().agreedPrice(new BigDecimal("330000")).build();

        product = Product.builder()
                .id("P-001")
                .name("Áo sơ mi Zara trắng")
                .size("M")
                .conditionNote("LIKE_NEW")
                .currentPrice(new BigDecimal("220000"))
                .status(ProductStatus.ON_SALE)
                .category(category)
                .brand(brand)
                .consignmentReceipt(receipt)
                .build();
    }

    @Test
    void getProducts_mapsAllFieldsIncludingThumbnail() {
        Page<Product> page = new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        ProductPhoto photo = ProductPhoto.builder()
                .product(product)
                .imageUrl("https://cdn.rehome.vn/products/P-001/thumb.jpg")
                .build();
        when(productPhotoRepository.findByProduct_IdInOrderByIdAsc(List.of("P-001")))
                .thenReturn(List.of(photo));

        PageResponse<ProductSummaryResponse> result = productService.getProducts(
                null, null, null, null, null, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        ProductSummaryResponse item = result.getContent().get(0);
        assertThat(item.getId()).isEqualTo("P-001");
        assertThat(item.getName()).isEqualTo("Áo sơ mi Zara trắng");
        assertThat(item.getBrandName()).isEqualTo("Zara");
        assertThat(item.getCategoryName()).isEqualTo("Áo sơ mi");
        assertThat(item.getSize()).isEqualTo("M");
        assertThat(item.getCondition()).isEqualTo("LIKE_NEW");
        assertThat(item.getCurrentPrice()).isEqualTo(new BigDecimal("220000"));
        assertThat(item.getOriginalPrice()).isEqualTo(new BigDecimal("330000"));
        assertThat(item.getThumbnailUrl()).isEqualTo("https://cdn.rehome.vn/products/P-001/thumb.jpg");
    }

    @Test
    void getProducts_returnsNullThumbnail_whenProductHasNoPhoto() {
        Page<Product> page = new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(productPhotoRepository.findByProduct_IdInOrderByIdAsc(List.of("P-001")))
                .thenReturn(List.of());

        PageResponse<ProductSummaryResponse> result = productService.getProducts(
                null, null, null, null, null, null, 0, 20);

        assertThat(result.getContent().get(0).getThumbnailUrl()).isNull();
    }

    @Test
    void getProducts_throwsBadRequest_whenPriceMinGreaterThanPriceMax() {
        assertThatThrownBy(() -> productService.getProducts(
                null, null, null, new BigDecimal("500000"), new BigDecimal("100000"), null, 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("priceMin không được lớn hơn priceMax.");

        verify(productRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }
}
