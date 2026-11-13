package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.rehome.rehome.client.GhnShippingFeeCalculator;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.CreateProductRequest;
import com.rehome.rehome.dto.request.DonateProductRequest;
import com.rehome.rehome.dto.request.PriceReductionRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.DegradedItemChoice;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.enums.DonationSource;
import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.marketplace.*;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ProductMapper;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.repository.spec.ProductSpecifications;
import com.rehome.rehome.service.NotificationService;
import com.rehome.rehome.service.ItemWithdrawalRequestService;
import com.rehome.rehome.service.ProductService;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.service.SkuLabelPdfService;
import com.rehome.rehome.service.SystemConfigReader;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    // Yêu cầu rút hàng còn sống (chờ duyệt hoặc đã duyệt, chờ lấy) - sản phẩm đang WITHDRAWN cả hai trường hợp
    private static final List<ItemWithdrawalStatus> ACTIVE_WITHDRAWAL_STATUSES =
            List.of(ItemWithdrawalStatus.PENDING_APPROVAL, ItemWithdrawalStatus.PENDING_PICKUP);

    private final ProductRepository productRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final ProductPriceChangeRepository productPriceChangeRepository;
    private final ProductMapper productMapper;
    private final EndOfTermDecisionRepository endOfTermDecisionRepository;
    private final ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    private final DegradedItemCaseRepository degradedItemCaseRepository;
    private final ConsignmentReceiptRepository receiptRepository;
    private final OrderItemRepository orderItemRepository;
    private final SkuLabelPdfService skuLabelPdfService;
    private final GhnShippingFeeCalculator ghnShippingFeeCalculator;
    private final SystemConfigReader systemConfigReader;
    private final DonationRepository donationRepository;
    private final CampaignRepository campaignRepository;
    private final ItemWithdrawalRequestService itemWithdrawalRequestService;
    private final QueueService queueService;
    private final NotificationService notificationService;
    private final AuditLogger auditLogger;

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // ================================================================ Sàn (public)

    @Override
    public PageResponse<ProductSummaryResponse> getProducts(String search, UUID categoryId, UUID brandId,
                                                            BigDecimal priceMin, BigDecimal priceMax,
                                                            UUID warehouseId, TryOnSlot tryonSlot,
                                                            int page, int size, UUID viewerId) {

        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            throw new BadRequestException("priceMin không được lớn hơn priceMax.");
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Product> result = productRepository.findAll(
                ProductSpecifications.filter(search, categoryId, brandId, priceMin, priceMax, warehouseId, tryonSlot),
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
    @Transactional
    public ProductDetailResponse updateProduct(Account moderator, String id,
                                               com.rehome.rehome.dto.request.UpdateProductRequest request) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        if (moderator.getRole() != Role.MODERATOR || moderator.getWarehouse() == null
                || product.getWarehouse() == null
                || !moderator.getWarehouse().getId().equals(product.getWarehouse().getId())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE);
        }
        if ((product.getStatus() != ProductStatus.ON_SALE && product.getStatus() != ProductStatus.WAREHOUSE)
                || !orderItemRepository.findActiveByProductIds(List.of(id)).isEmpty()) {
            throw new AppException(ErrorCode.PRODUCT_UPDATE_CONFLICT);
        }

        product.setName(request.name().strip());
        productRepository.save(product);
        List<ProductPhoto> existing = productPhotoRepository.findByProduct_IdOrderByDisplayOrderAscIdAsc(id);
        Map<String, java.util.ArrayDeque<ProductPhoto>> byUrl = new java.util.HashMap<>();
        for (ProductPhoto photo : existing) {
            byUrl.computeIfAbsent(photo.getImageUrl(), key -> new java.util.ArrayDeque<>()).add(photo);
        }
        List<ProductPhoto> photos = new ArrayList<>();
        for (String value : request.photoUrls()) {
            String url = value.strip();
            var matches = byUrl.get(url);
            ProductPhoto photo = matches == null || matches.isEmpty()
                    ? ProductPhoto.builder().product(product).imageUrl(url).build()
                    : matches.removeFirst();
            photo.setDisplayOrder(photos.size());
            photos.add(photo);
        }
        productPhotoRepository.deleteAll(byUrl.values().stream().flatMap(java.util.Collection::stream).toList());
        productPhotoRepository.saveAll(photos);
        return getProductDetail(id, moderator.getId());
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
                .tryonSlot(product.getCategory().getTryonSlot())
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

        // Yêu cầu rút đang chờ nhận hàng và ca hàng xuống cấp đang chờ quyết định, để app
        // hiện được "món này đang chờ rút / chờ bạn quyết định" ngay trên danh sách
        Map<String, ItemWithdrawalRequest> withdrawals = itemWithdrawalRequestRepository
                .findByProductIdsAndStatusIn(ids, ACTIVE_WITHDRAWAL_STATUSES).stream()
                .collect(Collectors.toMap(
                        w -> w.getProduct().getId(),
                        w -> w,
                        (first, second) -> first));

        Map<String, DegradedItemCase> degradedCases = degradedItemCaseRepository
                .findPendingByProductIds(ids).stream()
                .collect(Collectors.toMap(
                        c -> c.getProduct().getId(),
                        c -> c,
                        (first, second) -> first));

        List<String> donatedIds = page.getContent().stream()
                .filter(p -> p.getStatus() == ProductStatus.DONATED)
                .map(Product::getId)
                .toList();
        Map<String, MyProductResponse.DonationInfo> donations = donatedIds.isEmpty() ? Map.of()
                : donationRepository.findForProducts(donatedIds).stream().collect(Collectors.toMap(
                        d -> d.getProduct().getId(), this::toDonationInfo, (first, next) -> first));

        return page.map(p -> {
            MyProductResponse response = productMapper.toMyProduct(
                    p, photos.get(p.getId()), decisions.get(p.getId()),
                    hasOrderToShow(p.getStatus()) ? orderItems.get(p.getId()) : null,
                    withdrawals.get(p.getId()), degradedCases.get(p.getId()));
            if (p.getStatus() == ProductStatus.DONATED) {
                response.setDonation(donations.get(p.getId()));
            }
            return response;
        });
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

        ItemWithdrawalRequest withdrawal = itemWithdrawalRequestRepository
                .findByProductIdsAndStatusIn(List.of(id), ACTIVE_WITHDRAWAL_STATUSES).stream()
                .findFirst().orElse(null);

        DegradedItemCase degradedCase = degradedItemCaseRepository
                .findPendingByProductIds(List.of(id)).stream()
                .findFirst().orElse(null);

        MyProductResponse response = productMapper.toMyProductDetail(
                product, photo, decision, orderItem, withdrawal, degradedCase,
                changes.stream()
                        .map(c -> new MyProductResponse.PriceChange(
                                c.getChangedAt(), c.getOldPrice(), c.getNewPrice()))
                        .toList(),
                buildTermHistory(product, changes));
        if (product.getStatus() == ProductStatus.DONATED) {
            donationRepository.findForProducts(List.of(id)).stream().findFirst()
                    .map(this::toDonationInfo).ifPresent(response::setDonation);
        }
        return response;
    }

    private MyProductResponse.DonationInfo toDonationInfo(Donation donation) {
        Campaign campaign = donation.getCampaign();
        return new MyProductResponse.DonationInfo(donation.getId(),
                campaign == null ? null : campaign.getId(),
                campaign == null ? null : campaign.getTitle(),
                campaign == null ? null : campaign.getOrganization().getDisplayName(),
                donation.getStatus().name(), donation.getHandedOverAt());
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

        // Số lần giảm giá tối đa mỗi ngày do Admin cấu hình; đếm theo ngày VN từ lịch sử đổi giá
        LocalDate today = LocalDate.now(VN_ZONE);
        int maxPerDay = systemConfigReader.getInt(SystemConfigKeys.MAX_PRICE_CHANGES_PER_DAY,
                SystemConfigKeys.DEFAULT_MAX_PRICE_CHANGES_PER_DAY);
        long reducedToday = productPriceChangeRepository
                .countByProduct_IdAndChangedAtGreaterThanEqualAndChangedAtLessThan(
                        product.getId(),
                        today.atStartOfDay(VN_ZONE).toInstant(),
                        today.plusDays(1).atStartOfDay(VN_ZONE).toInstant());
        if (reducedToday >= maxPerDay) {
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
    @Transactional
    public DonateProductResponse donate(Account account, String productId, DonateProductRequest request) {

        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));                // 404

        if (!product.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.PRODUCT_FORBIDDEN);                           // 403
        }

        ProductStatus previousStatus = product.getStatus();
        Instant now = Instant.now();
        EndOfTermDecision decision = null;
        ItemWithdrawalRequest activeWithdrawal = null;
        DegradedItemCase degradedCase = null;
        String caseType;

        switch (previousStatus) {
            case ON_SALE -> caseType = "A";
            case WAREHOUSE -> {
                decision = endOfTermDecisionRepository.findByProductIds(List.of(productId)).stream()
                        .findFirst()
                        .filter(d -> d.getDecision() == null)
                        .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_DONATABLE));
                if (!degradedItemCaseRepository.findPendingByProductIds(List.of(productId)).isEmpty()) {
                    throw new AppException(ErrorCode.PRODUCT_NOT_DONATABLE);
                }
                caseType = "B";
            }
            case WITHDRAWN -> {
                activeWithdrawal = itemWithdrawalRequestRepository
                        .findFirstByProduct_IdAndStatusIn(productId, ACTIVE_WITHDRAWAL_STATUSES).orElse(null);
                if (activeWithdrawal != null) {
                    if (activeWithdrawal.getStatus() == ItemWithdrawalStatus.PENDING_APPROVAL) {
                        throw new AppException(ErrorCode.WITHDRAWAL_PENDING_APPROVAL);
                    }
                    caseType = "C1";
                } else {
                    decision = endOfTermDecisionRepository.findByProductIds(List.of(productId)).stream()
                            .findFirst().filter(d -> d.getDecision() == EndOfTermChoice.TAKE_BACK
                                    && d.getTakenBackAt() == null).orElse(null);
                    degradedCase = degradedItemCaseRepository.findFirstByProduct_IdOrderByCreatedAtDesc(productId)
                            .filter(c -> c.getChoice() == DegradedItemChoice.PARTIAL_WITH_ITEM_BACK
                                    && c.getPickedUpAt() == null && !c.isItemDonated()).orElse(null);
                    if (degradedCase != null && (decision == null
                            || degradedCase.getDecidedAt().isAfter(decision.getDecidedAt()))) {
                        decision = null;
                        caseType = "C3";
                    } else if (decision != null) {
                        degradedCase = null;
                        caseType = "C2";
                    } else {
                        throw new AppException(ErrorCode.PRODUCT_NOT_DONATABLE);
                    }
                }
            }
            default -> throw new AppException(ErrorCode.PRODUCT_NOT_DONATABLE);
        }

        if (orderItemRepository.findActiveByProductIds(List.of(productId)).stream()
                .map(OrderItem::getOrder)
                .anyMatch(order -> blocksDonation(order, now))) {
            throw new AppException(ErrorCode.PRODUCT_NOT_DONATABLE);
        }

        Campaign campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> new AppException(ErrorCode.CAMPAIGN_NOT_ACCEPTING));
        if (campaign.getStatus() == CampaignStatus.HIDDEN) {
            throw new AppException(ErrorCode.CAMPAIGN_HIDDEN);
        }
        if (campaign.getStatus() != CampaignStatus.OPEN || campaign.getEndTime() == null
                || !campaign.getEndTime().isAfter(now)) {
            throw new AppException(ErrorCode.CAMPAIGN_NOT_ACCEPTING);
        }
        Warehouse warehouse = product.getWarehouse();
        if (warehouse == null || campaign.getWarehouses().stream().noneMatch(w -> w.getId().equals(warehouse.getId()))) {
            throw new AppException(ErrorCode.CAMPAIGN_NOT_AT_WAREHOUSE);
        }

        product.setStatus(ProductStatus.DONATED);
        productRepository.save(product);

        if (decision != null) {
            decision.setDecision(EndOfTermChoice.DONATE);
            decision.setDecidedAt(now);
            endOfTermDecisionRepository.save(decision);
        }
        if (degradedCase != null) {
            degradedCase.setItemDonated(true);
            degradedItemCaseRepository.save(degradedCase);
        }
        if (activeWithdrawal != null) {
            activeWithdrawal.setStatus(ItemWithdrawalStatus.CONVERTED_TO_DONATION);
            itemWithdrawalRequestRepository.saveAndFlush(activeWithdrawal);
            itemWithdrawalRequestService.refundDonationFee(activeWithdrawal);
            queueService.dequeue(QueueType.WITHDRAWAL, activeWithdrawal.getId().toString());
        }

        String donorName = request.isAnonymous() ? null : memberProfileRepository.findByAccountId(account.getId())
                .map(MemberProfile::getFullName).orElse(null);

        Donation donation = donationRepository.save(Donation.pendingAt(warehouse, campaign, donorName,
                        request.isAnonymous(), "Món ký gửi: " + product.getName() + " · SKU " + productId)
                .product(product)
                .source(DonationSource.CONSIGNED_ITEM)
                .build());

        queueService.enqueue(QueueType.DONATION_PREP, donation.getId().toString(), warehouse);
        auditLogger.log(account, "DONATE_CONSIGNED_ITEM", "PRODUCT", productId,
                JsonNodeFactory.instance.objectNode()
                        .put("productId", productId)
                        .put("campaignId", campaign.getId().toString())
                        .put("previousStatus", previousStatus.name())
                        .put("caseType", caseType));
        notificationService.notify(
                campaign.getOrganization().getAccount(), NotificationType.DONATION_RECORDED,
                "Có lượt quyên góp mới",
                "Có 1 món ký gửi được quyên góp cho chiến dịch của bạn tại " + warehouse.getName(),
                NotificationTargetType.CAMPAIGN, campaign.getId(), warehouse.getId());

        return new DonateProductResponse(product.getId(), product.getStatus(), donation.getId(),
                campaign.getId(), campaign.getTitle(), campaign.getOrganization().getDisplayName());
    }

    private boolean blocksDonation(Order order, Instant now) {
        return switch (order.getStatus()) {
            case PENDING_PAYMENT, PAID, PACKING, SHIPPING -> true;
            case DELIVERED, COMPLETED -> {
                Instant deadline = order.getReturnDeadline();
                if (deadline == null && order.getDeliveredAt() != null) {
                    deadline = order.getDeliveredAt().plus(systemConfigReader.getInt(
                            SystemConfigKeys.RETURN_REQUEST_WINDOW_DAYS,
                            SystemConfigKeys.DEFAULT_RETURN_REQUEST_WINDOW_DAYS), ChronoUnit.DAYS);
                }
                yield deadline != null && !now.isAfter(deadline);
            }
            case CANCELLED, RETURNED -> false;
        };
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

        List<String> photos = productPhotoRepository
                .findByProduct_IdOrderByDisplayOrderAscIdAsc(product.getId())
                .stream()
                .map(ProductPhoto::getImageUrl)
                .toList();

        ConsignmentReceipt receipt = product.getConsignmentReceipt();

        // Mã Member và họ tên nằm ở MemberProfile (Account không giữ)
        Account member = product.getMember();
        MemberProfile profile = member == null ? null
                : memberProfileRepository.findByAccountId(member.getId()).orElse(null);

        UUID decisionId = endOfTermDecisionRepository
                .findFirstByProduct_IdAndDecisionIsNullOrderByDeadlineDesc(product.getId())
                .map(EndOfTermDecision::getId)
                .orElse(null);
        UUID degradedCaseId = degradedItemCaseRepository.findPendingByProductIds(List.of(product.getId()))
                .stream().findFirst().map(DegradedItemCase::getId).orElse(null);

        return ProductSkuLookupResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .status(product.getStatus())
                .brandName(product.getBrand().getName())
                .categoryName(product.getCategory().getName())
                .size(product.getSize())
                .conditionNote(product.getConditionNote())
                .currentPrice(product.getCurrentPrice())
                .originalPrice(receipt.getAgreedPrice())
                .floorPrice(receipt.getFloorPrice())
                .currentTerm(product.getCurrentTerm())
                .daysIntoTerm(daysIntoCurrentTerm(product))
                .termLengthDays(receipt.getTermLengthDays())
                .commissionRateTerm1(receipt.getCommissionRateTerm1())
                .commissionRateTerm2(receipt.getCommissionRateTerm2())
                .commissionRateTerm3(receipt.getCommissionRateTerm3())
                .degradedCompensationRate(receipt.getDegradedCompensationRate())
                .memberCode(profile != null ? profile.getMemberCode() : null)
                .memberFullName(profile != null ? profile.getFullName() : null)
                .stockInDate(product.getCreatedAt())
                .photos(photos)
                .decisionId(decisionId)
                .degradedCaseId(degradedCaseId)
                .build();
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
                                                            boolean degradedOnly, int page, int size) {

        if (account.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);              // 403
        }

        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Product> result = productRepository.findInventory(
                account.getWarehouse().getId(), status, degradedOnly, pageable);

        return PageResponse.from(result.map(p -> InventoryItemResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .status(p.getStatus())
                .agreedPrice(p.getConsignmentReceipt().getAgreedPrice())
                .termLengthDays(p.getConsignmentReceipt().getTermLengthDays())
                .currentTerm(p.getCurrentTerm())
                .daysIntoTerm(daysIntoCurrentTerm(p))
                .currentPrice(p.getCurrentPrice())
                .build()));
    }

    // Số ngày đã trôi qua trong kỳ ký gửi hiện tại (tối thiểu 1); dùng chung cho API 40 và API 41.
    // null khi sản phẩm chưa có mốc bắt đầu kỳ (dữ liệu cũ)
    private static Integer daysIntoCurrentTerm(Product product) {
        if (product.getTermStartedAt() == null) {
            return null;
        }
        return Math.max(1, (int) ChronoUnit.DAYS.between(product.getTermStartedAt(), Instant.now()));
    }
}