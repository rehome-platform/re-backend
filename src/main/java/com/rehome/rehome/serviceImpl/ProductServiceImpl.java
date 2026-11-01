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
import com.rehome.rehome.util.ConsignmentCommission;
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
    private final ProductPriceChangeRepository productPriceChangeRepository;
    private final ProductMapper productMapper;
    private final EndOfTermDecisionRepository endOfTermDecisionRepository;
    private final ConsignmentReceiptRepository receiptRepository;
    private final OrderItemRepository orderItemRepository;
    private final StockMovementLogRepository stockMovementLogRepository;
    private final SkuLabelPdfService skuLabelPdfService;
    private final GhnShippingFeeCalculator ghnShippingFeeCalculator;

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // ================================================================ Sàn (public)

    @Override
    public PageResponse<ProductSummaryResponse> getProducts(String search, UUID categoryId, UUID brandId,
                                                            BigDecimal priceMin, BigDecimal priceMax,
                                                            UUID warehouseId, int page, int size,
                                                            UUID viewerId) {

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
                .categoryName(product.getCategory().getName())
                .size(product.getSize())
                .condition(product.getConditionNote())
                .originalPrice(product.getConsignmentReceipt().getAgreedPrice())
                .currentPrice(product.getCurrentPrice())
                .isMine(isOwnedBy(product, viewerId))
                .build());

        return PageResponse.from(mapped);
    }

    @Override
    public ProductDetailResponse getProductDetail(String id, UUID viewerId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        List<String> photos = productPhotoRepository.findByProduct_IdOrderByDisplayOrderAscIdAsc(id).stream()
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
                .isMine(isOwnedBy(product, viewerId))
                .build();
    }

    // Người ký gửi không được mua lại món của chính mình (chặn thật ở CartService/OrderService),
    // cờ này chỉ để FE ẩn nút trước. Null-safe hai đầu: khách chưa đăng nhập -> false.
    private static boolean isOwnedBy(Product product, UUID viewerId) {
        return viewerId != null && viewerId.equals(product.getMember().getId());
    }

    // RESERVED (từ lúc đặt hàng) và SOLD (từ lúc buyer xác nhận nhận hàng) đều gắn với đúng 1 đơn
    // đang active - hiện order sớm từ RESERVED để người ký gửi thấy mốc tiền vào ví (payoutDueAt)
    // ngay khi GHN báo giao, không phải chờ tới lúc buyer bấm nhận.
    private static boolean hasOrderToShow(ProductStatus status) {
        return status == ProductStatus.RESERVED || status == ProductStatus.SOLD;
    }

    // ================================================================ Hàng ký gửi của tôi

    @Override
    public Page<MyProductResponse> getMyProducts(UUID memberId, ProductStatus status,
                                                 boolean pendingDecision, Pageable pageable) {

        Page<Product> page = productRepository.findMine(memberId, status, pendingDecision, pageable);

        List<String> ids = page.getContent().stream().map(Product::getId).toList();
        if (ids.isEmpty()) {
            return page.map(p -> null);
        }

        // Mỗi loại dữ liệu phụ lấy bằng một query cho cả trang, tránh N+1
        Map<String, String> photos = productPhotoRepository
                .findByProduct_IdInOrderByDisplayOrderAscIdAsc(ids).stream()
                .collect(Collectors.toMap(
                        ph -> ph.getProduct().getId(),
                        ProductPhoto::getImageUrl,
                        (first, second) -> first));

        // findByProductIds sắp deadline giảm dần -> giữ dòng đầu = dòng mới nhất của sản phẩm
        Map<String, EndOfTermDecision> decisions = endOfTermDecisionRepository.findByProductIds(ids).stream()
                .collect(Collectors.toMap(
                        d -> d.getProduct().getId(),
                        d -> d,
                        (first, second) -> first));

        // Giữ cả OrderItem (không chỉ Order) vì hoa hồng chốt và kỳ đặt nằm ở dòng đơn
        Map<String, OrderItem> orderItems = orderItemRepository.findActiveByProductIds(ids).stream()
                .collect(Collectors.toMap(
                        oi -> oi.getProduct().getId(),
                        oi -> oi,
                        (first, second) -> first));

        return page.map(p -> productMapper.toMyProduct(
                p,
                photos.get(p.getId()),
                decisions.get(p.getId()),
                hasOrderToShow(p.getStatus()) ? orderItems.get(p.getId()) : null));
    }

    @Override
    public MyProductResponse getMyProduct(UUID memberId, String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));            // 404

        // Chỉ chủ hàng được xem
        if (!product.getMember().getId().equals(memberId)) {
            throw new AppException(ErrorCode.PRODUCT_FORBIDDEN);                              // 403
        }

        List<ProductPriceChange> changes =
                productPriceChangeRepository.findByProduct_IdOrderByChangedAtAsc(id);

        String photo = productPhotoRepository
                .findByProduct_IdInOrderByDisplayOrderAscIdAsc(List.of(id)).stream()
                .map(ProductPhoto::getImageUrl)
                .findFirst().orElse(null);

        EndOfTermDecision decision = endOfTermDecisionRepository.findByProductIds(List.of(id)).stream()
                .findFirst().orElse(null);

        OrderItem orderItem = hasOrderToShow(product.getStatus())
                ? orderItemRepository.findActiveByProductIds(List.of(id)).stream()
                .findFirst().orElse(null)
                : null;

        return productMapper.toMyProductDetail(
                product, photo, decision, orderItem,
                changes.stream()
                        .map(c -> new MyProductResponse.PriceChange(
                                c.getChangedAt(), c.getOldPrice(), c.getNewPrice()))
                        .toList(),
                buildTermHistory(product, changes));
    }

    // price = giá có hiệu lực lúc kỳ đó bắt đầu. Mốc đầu các kỳ trước suy ra bằng cách
    // lùi từ termStartedAt của kỳ hiện tại theo độ dài kỳ.
    private List<MyProductResponse.TermInfo> buildTermHistory(Product p, List<ProductPriceChange> changesAsc) {
        ConsignmentReceipt r = p.getConsignmentReceipt();
        int current = p.getCurrentTerm();
        List<MyProductResponse.TermInfo> out = new ArrayList<>();

        for (int term = 1; term <= current; term++) {
            Instant start = p.getTermStartedAt()
                    .minus(Duration.ofDays((long) (current - term) * r.getTermLengthDays()));

            BigDecimal price = r.getAgreedPrice();
            for (ProductPriceChange c : changesAsc) {
                if (!c.getChangedAt().isAfter(start)) {
                    price = c.getNewPrice();
                }
            }
            out.add(new MyProductResponse.TermInfo(
                    term, price, ConsignmentCommission.rateForTerm(r, term)));
        }
        return out;
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

        BigDecimal oldPrice = product.getCurrentPrice();
        Instant now = Instant.now();

        product.setCurrentPrice(newPrice);
        product.setLastPriceReducedOn(today);
        product.setLastPriceChangedAt(now);
        productRepository.save(product);

        productPriceChangeRepository.save(ProductPriceChange.builder()
                .product(product)
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .changedAt(now)
                .build());

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

    // ================================================================ Kho (Moderator)

    @Override
    @Transactional
    public ProductResponse create(CreateProductRequest request, Account account) {
        ConsignmentReceipt receipt = receiptRepository.findById(request.getReceiptId())
                .orElseThrow(() -> new AppException(ErrorCode.RECEIPT_NOT_FOUND));          // 404

        // Moderator chỉ nhập kho biên nhận thuộc kho mình phụ trách
        if (account.getWarehouse() == null
                || !account.getWarehouse().getId().equals(receipt.getWarehouse().getId())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE);                 // 403
        }
        if (receipt.getConfirmedAt() == null) {
            throw new AppException(ErrorCode.RECEIPT_NOT_CONFIRMED);                         // 409
        }
        if (productRepository.existsByConsignmentReceiptId(receipt.getId())) {
            throw new AppException(ErrorCode.RECEIPT_ALREADY_HAS_PRODUCT);                   // 409
        }

        ConsignmentForm form = receipt.getConsignmentForm();
        Instant now = Instant.now();

        // Moderator không nhập size thì lấy size Member đã khai trong form
        String size = StringUtils.hasText(request.getSize()) ? request.getSize() : form.getSize();

        Product product = Product.builder()
                .id(generateSku(receipt.getWarehouse(), form.getCategory()))
                .consignmentReceipt(receipt)
                .member(receipt.getMember())
                .warehouse(receipt.getWarehouse())
                .category(form.getCategory())
                .brand(form.getBrand())
                .name(request.getName().trim())
                .size(size)
                .conditionNote(receipt.getConditionNote())
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
    public List<StockMovementResponse> getStockMovements(String productId) {
        if (!productRepository.existsById(productId)) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);                  // 404
        }
        return stockMovementLogRepository.findByProduct_IdOrderByCreatedAtDesc(productId).stream()
                .map(l -> new StockMovementResponse(l.getId(), l.getMovementType(),
                        l.getFromLocation(), l.getToLocation(), l.getNote(), l.getCreatedAt()))
                .toList();
    }

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

    @Override
    public ProductSkuLookupResponse getBySku(Account account, String sku) {
        Product product = productRepository.findById(sku)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));    // 404

        assertSameWarehouse(account, product);                                        // 403

        return new ProductSkuLookupResponse(product.getId(), product.getName(),
                product.getShelfLocation(), product.getStatus());
    }

    @Override
    public PdfDownload getSkuLabel(Account account, String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));    // 404

        assertSameWarehouse(account, product);                                        // 403

        return new PdfDownload("sku-" + product.getId() + ".pdf", skuLabelPdfService.generate(product));
    }

    @Override
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