package com.rehome.rehome.service;

import com.rehome.rehome.component.ProductListCache;
import com.rehome.rehome.component.FeaturedListingManager;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ProductSummaryResponse;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.spec.ProductSpecifications;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Keep the JDBC connection only while loading and mapping the catalogue, never while calling Redis. */
@Service
@RequiredArgsConstructor
public class ProductCatalogReader {
    private final ProductRepository productRepository;
    private final ProductPhotoRepository productPhotoRepository;

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> load(ProductListCache.Key key) {
        PageRequest pageable = PageRequest.of(key.page(), key.size());

        Page<Product> result = productRepository.findAll(
                ProductSpecifications.filter(key.search(), key.categoryId(), key.brandId(), key.priceMin(), key.priceMax(),
                                key.warehouseId(), key.tryonSlot())
                        .and(ProductSpecifications.featuredFirst(Instant.now())),
                pageable
        );

        List<String> productIds = result.getContent().stream().map(Product::getId).toList();
        Map<String, String> thumbnailByProductId = productIds.isEmpty() ? Map.of() : productPhotoRepository
                .findByProduct_IdInOrderByDisplayOrderAscIdAsc(productIds).stream()
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
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .tryonSlot(product.getCategory().getTryonSlot())
                .size(product.getSize())
                .condition(product.getConditionNote())
                .originalPrice(product.getConsignmentReceipt().getAgreedPrice())
                .currentPrice(product.getCurrentPrice())
                .isMine(key.viewerId() != null && key.viewerId().equals(product.getMember().getId()))
                .featured(FeaturedListingManager.isFeatured(product))
                .build());

        return PageResponse.from(mapped);
    }

}
