package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.CreateProductRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.*;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ProductMapper;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.spec.ProductSpecifications;
import com.rehome.rehome.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
    private final ProductMapper productMapper;
    private final EndOfTermDecisionRepository endOfTermDecisionRepository;
    private final ConsignmentReceiptRepository receiptRepository;



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

    @Override
    @Transactional(readOnly = true)
    public Page<MyProductResponse> getMyProducts(UUID memberId, ProductStatus status, Pageable pageable) {
        return productRepository.findMine(memberId, status, pageable)
                .map(productMapper::toMyProductResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductEndOfTermDetailResponse getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        EndOfTermDecision pending = endOfTermDecisionRepository
                .findFirstByProduct_IdAndDecisionIsNullOrderByDeadlineDesc(id)
                .orElse(null);
        return productMapper.toEndOfTermDetailResponse(product, pending);
    }
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        ConsignmentReceipt receipt = receiptRepository.findById(request.getReceiptId())
                .orElseThrow(() -> new AppException(ErrorCode.RECEIPT_NOT_FOUND));          // 404

        if (receipt.getConfirmedAt() == null) {
            throw new AppException(ErrorCode.RECEIPT_NOT_CONFIRMED);                         // 409
        }
        if (productRepository.existsByConsignmentReceiptId(receipt.getId())) {
            throw new AppException(ErrorCode.RECEIPT_ALREADY_HAS_PRODUCT);                   // 409
        }

        ConsignmentForm form = receipt.getConsignmentForm();
        Instant now = Instant.now();

        Product product = Product.builder()
                .id(generateSku(receipt.getWarehouse(), form.getCategory()))
                .consignmentReceipt(receipt)
                .member(receipt.getMember())
                .warehouse(receipt.getWarehouse())
                .category(form.getCategory())
                .brand(form.getBrand())
                .name(request.getName().trim())
                .size(request.getSize())
                .conditionNote(form.getDescription())            // xem lưu ý 2
                .currentPrice(receipt.getAgreedPrice())
                .status(ProductStatus.ON_SALE)
                .currentTerm((short) 1)
                .termStartedAt(now)
                .endOfTermDeadline(now.plus(Duration.ofDays(
                        (long) receipt.getTermLengthDays() * (receipt.getMaxRenewals() + 1))))
                .shelfLocation(request.getShelfLocation())
                .build();

        productRepository.save(product);
        savePhotos(product, request.getPhotoUrls());

        return new ProductResponse(product.getId(), product.getName(), product.getStatus());
    }

    private String generateSku(Warehouse warehouse, Category category) {
        // Q7-AK-000845 = <mã kho>-<mã danh mục>-<số thứ tự 6 chữ số>
        return "%s-%s-%06d".formatted(
                warehouse.getCode(), category.getCode(), productRepository.nextSkuSequence());
    }

    private void savePhotos(Product product, List<String> urls) {
        if (urls == null || urls.isEmpty()) return;
        List<ProductPhoto> photos = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            ProductPhoto p = new ProductPhoto();
            p.setProduct(product);
            p.setImageUrl(urls.get(i));
            p.setDisplayOrder(i);   // ảnh đầu tiên là ảnh đại diện
            photos.add(p);
        }
        productPhotoRepository.saveAll(photos);
    }
}
