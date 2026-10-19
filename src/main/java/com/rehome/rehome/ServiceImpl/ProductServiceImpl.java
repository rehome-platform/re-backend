package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ProductDetailResponse;
import com.rehome.rehome.dto.response.ProductSummaryResponse;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.spec.ProductSpecifications;
import com.rehome.rehome.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductPhotoRepository productPhotoRepository;

    @Override
    public PageResponse<ProductSummaryResponse> getProducts(String search, UUID categoryId, UUID brandId,
                                                              BigDecimal priceMin, BigDecimal priceMax,
                                                              UUID warehouseId, int page, int size) {

        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            throw new BadRequestException("priceMin không được lớn hơn priceMax.");
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Product> result = productRepository.findAll(
                ProductSpecifications.filter(search, categoryId, brandId, priceMin, priceMax, warehouseId),
                pageable
        );

        List<String> productIds = result.getContent().stream().map(Product::getId).toList();
        Map<String, String> thumbnailByProductId = productPhotoRepository
                .findByProduct_IdInOrderByIdAsc(productIds).stream()
                .collect(Collectors.toMap(
                        photo -> photo.getProduct().getId(),
                        ProductPhoto::getImageUrl,
                        (first, second) -> first
                ));

        Page<ProductSummaryResponse> mapped = result.map(product -> ProductSummaryResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .thumbnailUrl(thumbnailByProductId.get(product.getId()))
                .brandName(product.getBrand().getName())
                .categoryName(product.getCategory().getName())
                .size(product.getSize())
                .condition(product.getConditionNote())
                .originalPrice(product.getConsignmentReceipt().getAgreedPrice())
                .currentPrice(product.getCurrentPrice())
                .build());

        return PageResponse.from(mapped);
    }

    @Override
    public ProductDetailResponse getProductDetail(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));

        List<String> photos = productPhotoRepository.findByProduct_IdOrderByIdAsc(id).stream()
                .map(ProductPhoto::getImageUrl)
                .toList();

        return ProductDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .currentPrice(product.getCurrentPrice())
                .originalPrice(product.getConsignmentReceipt().getAgreedPrice())
                .status(product.getStatus())
                .brandName(product.getBrand().getName())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .size(product.getSize())
                .condition(product.getConditionNote())
                .description(null)
                .inspectionDate(product.getConsignmentReceipt().getConfirmedAt())
                .warehouseId(product.getWarehouse().getId())
                .warehouseName(product.getWarehouse().getName())
                .photos(photos)
                .build();
    }
}
