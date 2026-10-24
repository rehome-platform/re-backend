package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnShippingFeeCalculator;
import com.rehome.rehome.dto.request.CreateProductRequest;
import com.rehome.rehome.dto.request.PriceReductionRequest;
import com.rehome.rehome.dto.request.UpdateShelfLocationRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.StockMovementType;
import com.rehome.rehome.entity.marketplace.*;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ProductMapper;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.repository.spec.ProductSpecifications;
import com.rehome.rehome.service.ProductService;
import com.rehome.rehome.service.SkuLabelPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final StockMovementLogRepository stockMovementLogRepository;
    private final SkuLabelPdfService skuLabelPdfService;
    private final GhnShippingFeeCalculator ghnShippingFeeCalculator;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");


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
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

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
    public ProductResponse create(CreateProductRequest request, Account account) {
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
                .conditionNote(receipt.getConditionNote())            // xem lưu ý 2
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

        stockMovementLogRepository.save(StockMovementLog.builder()
                .product(product)
                .warehouse(product.getWarehouse())
                .movementType(StockMovementType.STOCK_IN)
                .toLocation(product.getShelfLocation())
                .moderator(account)
                .build());

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

    @Override
    @Transactional
    public PriceReductionResponse reducePrice(Account account, String productId, PriceReductionRequest request) {

        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));               // 404

        if (!product.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.PRODUCT_FORBIDDEN);                                 // 403
        }
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new AppException(ErrorCode.PRODUCT_NOT_ON_SALE);                               // 409
        }

        BigDecimal newPrice = request.getNewPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal floorPrice = product.getConsignmentReceipt().getFloorPrice();

        if (newPrice.compareTo(product.getCurrentPrice()) >= 0) {
            throw new AppException(ErrorCode.INVALID_NEW_PRICE);                                 // 400
        }
        if (newPrice.compareTo(floorPrice) < 0) {
            throw new AppException(ErrorCode.BELOW_FLOOR_PRICE);                                 // 409
        }

        LocalDate today = LocalDate.now(VN_ZONE);
        if (today.equals(product.getLastPriceReducedOn())) {
            throw new AppException(ErrorCode.PRICE_CHANGE_LIMIT_REACHED);                        // 409
        }

        product.setCurrentPrice(newPrice);
        product.setLastPriceReducedOn(today);
        productRepository.save(product);

        return new PriceReductionResponse(product.getCurrentPrice());
    }

    @Override
    public ShippingFeePreviewResponse getShippingFeePreview(String productId, String wardName, String provinceName) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));

        if (!StringUtils.hasText(wardName) || !StringUtils.hasText(provinceName)) {
            throw new BadRequestException("wardName và provinceName là bắt buộc.");
        }

        Warehouse warehouse = product.getWarehouse();
        BigDecimal shippingFee = ghnShippingFeeCalculator.calculate(warehouse, wardName, provinceName);

        return ShippingFeePreviewResponse.builder()
                .productId(product.getId())
                .warehouseId(warehouse.getId().toString())
                .warehouseName(warehouse.getName())
                .wardName(wardName)
                .provinceName(provinceName)
                .shippingFee(shippingFee)
                .build();
    }

    // ProductServiceImpl
    @Override
    @Transactional(readOnly = true)
    public List<StockMovementResponse> getStockMovements(String productId) {
        if (!productRepository.existsById(productId)) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);                  // 404
        }
        return stockMovementLogRepository.findByProduct_IdOrderByCreatedAtDesc(productId).stream()
                .map(l -> new StockMovementResponse(l.getId(), l.getMovementType(),
                        l.getFromLocation(), l.getToLocation(), l.getNote(), l.getCreatedAt()))
                .toList();
    }

    // ProductServiceImpl
    @Override
    @Transactional
    public ShelfLocationResponse updateShelfLocation(Account account, String productId,
                                                     UpdateShelfLocationRequest request) {

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));        // 404

        // Moderator chỉ được đổi kệ sản phẩm trong kho của mình
        assertSameWarehouse(account, product);

        String from = product.getShelfLocation();
        String to = request.getShelfLocation().trim();

        if (!to.equals(from)) {
            product.setShelfLocation(to);
            productRepository.save(product);

            stockMovementLogRepository.save(StockMovementLog.builder()
                    .product(product)
                    .warehouse(product.getWarehouse())
                    .movementType(StockMovementType.SHELF_MOVE)
                    .fromLocation(from)
                    .toLocation(to)
                    .moderator(account)
                    .build());
        }

        return new ShelfLocationResponse(product.getId(), product.getShelfLocation());
    }

    // Moderator chỉ thao tác được sản phẩm thuộc kho của mình
    private void assertSameWarehouse(Account account, Product product) {
        if (account.getWarehouse() == null
                || !account.getWarehouse().getId().equals(product.getWarehouse().getId())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE);   // 403
        }
    }
    // ProductServiceImpl
    @Override
    @Transactional(readOnly = true)
    public ProductSkuLookupResponse getBySku(Account account, String sku) {
        Product product = productRepository.findById(sku)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));    // 404

        assertSameWarehouse(account, product);                                        // 403

        return new ProductSkuLookupResponse(product.getId(), product.getName(),
                product.getShelfLocation(), product.getStatus());
    }
    @Override
    @Transactional(readOnly = true)
    public SkuLabelResponse getSkuLabel(Account account, String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));    // 404

        assertSameWarehouse(account, product);                                        // 403

        return new SkuLabelResponse(skuLabelPdfService.generate(product));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> getInventory(Account account, ProductStatus status,
                                                            String shelfLocation, int page, int size) {

        if (account.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);              // 403
        }

        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        String shelf = (shelfLocation == null || shelfLocation.isBlank()) ? null : shelfLocation.trim();

        Page<Product> result = productRepository.findInventory(
                account.getWarehouse().getId(), status, shelf, pageable);

        return PageResponse.from(result.map(p -> new InventoryItemResponse(
                p.getId(), p.getName(), p.getShelfLocation(), p.getStatus())));
    }
}
