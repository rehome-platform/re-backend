package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.util.NotificationFormatting;

import com.rehome.rehome.entity.enums.NotificationType;

import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.component.VoucherCalculator;
import com.rehome.rehome.client.GhnShippingFeeCalculator;
import com.rehome.rehome.client.ShippingFeeMemo;
import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.request.GhnCreateOrderRequest;
import com.rehome.rehome.dto.request.GhnOrderItem;
import com.rehome.rehome.dto.request.PackScanRequest;
import com.rehome.rehome.dto.request.PayOrdersRequest;
import com.rehome.rehome.dto.request.RateOrderRequest;
import com.rehome.rehome.dto.request.UpdateOrderShippingAddressRequest;
import com.rehome.rehome.dto.response.CreateShipmentResponse;
import com.rehome.rehome.dto.response.GhnGenTokenResponse;
import com.rehome.rehome.dto.response.ShipmentLabelResponse;
import com.rehome.rehome.dto.request.GhnGenTokenRequest;
import com.rehome.rehome.dto.response.GhnCreateOrderResponse;
import com.rehome.rehome.dto.response.OrderCancelResponse;
import com.rehome.rehome.dto.response.OrderConfirmReceiptResponse;
import com.rehome.rehome.dto.response.OrderDetailItemResponse;
import com.rehome.rehome.dto.response.OrderDetailResponse;
import com.rehome.rehome.dto.response.OrderListResponse;
import com.rehome.rehome.dto.response.OrderRatingResponse;
import com.rehome.rehome.dto.response.OrderResponse;
import com.rehome.rehome.dto.response.OrderSummaryResponse;
import com.rehome.rehome.dto.response.OrderTrackingEventResponse;
import com.rehome.rehome.dto.response.OrderTrackingResponse;
import com.rehome.rehome.dto.response.PackScanResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.PaidOrderResponse;
import com.rehome.rehome.dto.response.PayOrdersResponse;
import com.rehome.rehome.dto.response.SoldOrderItemResponse;
import com.rehome.rehome.dto.response.SoldOrderListResponse;
import com.rehome.rehome.dto.response.SoldOrderResponse;
import com.rehome.rehome.dto.response.WarehouseOrderListResponse;
import com.rehome.rehome.dto.response.WarehouseOrderResponse;
import com.rehome.rehome.dto.response.WrongItemDetail;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CancelReason;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.enums.ReturnedItemAction;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.entity.marketplace.Cart;
import com.rehome.rehome.entity.marketplace.CartItem;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.OrderTrackingEvent;
import com.rehome.rehome.entity.marketplace.Payment;
import com.rehome.rehome.entity.marketplace.PlatformFund;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.exception.GhnException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CartItemRepository;
import com.rehome.rehome.repository.CartRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.MemberVoucherRepository;
import com.rehome.rehome.repository.OrderTrackingEventRepository;
import com.rehome.rehome.repository.PaymentRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.QueueItemRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import com.rehome.rehome.service.OrderService;
import com.rehome.rehome.util.CloudinaryImageUrls;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.entity.enums.WarehouseAuditGroup;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.service.SystemConfigReader;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final com.rehome.rehome.component.BusinessNotifications businessNotifications;


    private static final Set<String> ISSUE_GHN_STATUSES = Set.of(
            "delivery_fail", "waiting_to_return", "return", "return_transporting", "return_sorting",
            "returning", "return_fail", "returned", "exception", "lost", "damage", "scrap", "cancel");

    private static final List<ReturnRequestStatus> OPEN_RETURN_STATUSES = List.of(
            ReturnRequestStatus.PENDING,
            ReturnRequestStatus.APPROVED,
            ReturnRequestStatus.SHIPPED_BACK,
            ReturnRequestStatus.INSPECTING);

    private final com.rehome.rehome.service.ContentModerationService moderation;
    private final com.rehome.rehome.service.PointsService pointsService;
    private final AccountRepository accountRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderTrackingEventRepository orderTrackingEventRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final PaymentRepository paymentRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PlatformFundRepository platformFundRepository;
    private final QueueService queueService;
    private final QueueItemRepository queueItemRepository;
    private final GhnApi ghnApi;
    private final GhnShippingFeeCalculator ghnShippingFeeCalculator;
    private final SystemConfigReader systemConfigReader;
    private final MemberVoucherRepository memberVoucherRepository;
    private final VoucherCalculator voucherCalculator;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    @Value("${app.order.tax-rate:0}")
    private BigDecimal taxRate;

    @Value("${ghn.token}")
    private String ghnToken;

    @Value("${ghn.shop-id}")
    private String ghnShopId;

    @Value("${ghn.base-url}")
    private String ghnBaseUrl;

    @Value("${ghn.default-package.weight-grams}")
    private int defaultPackageWeightGrams;

    @Value("${ghn.default-package.length-cm}")
    private int defaultPackageLengthCm;

    @Value("${ghn.default-package.width-cm}")
    private int defaultPackageWidthCm;

    @Value("${ghn.default-package.height-cm}")
    private int defaultPackageHeightCm;

    @Override
    @Transactional
    public List<OrderResponse> createOrders(String email, CreateOrderRequest request) {

        if (!StringUtils.hasText(request.getShippingAddressId())) {
            throw new BadRequestException("shippingAddressId là bắt buộc.");
        }

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        UUID shippingAddressId;
        try {
            shippingAddressId = UUID.fromString(request.getShippingAddressId());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("shippingAddressId không hợp lệ.");
        }

        ShippingAddress shippingAddress = shippingAddressRepository.findById(shippingAddressId)
                .filter(a -> a.getMember().getId().equals(buyer.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ."));

        List<String> requestedProductIds = request.getProductIds();
        boolean buyNow = requestedProductIds != null && !requestedProductIds.isEmpty();

        List<Product> lockedProducts = new ArrayList<>();
        Set<String> productIdsFromCart = new HashSet<>();
        UUID cartId = null;

        if (buyNow) {
            for (String productId : requestedProductIds) {
                lockedProducts.add(lockAndValidateProduct(productId, buyer.getId()));
            }

            Cart cart = cartRepository.findByMember_Id(buyer.getId()).orElse(null);
            if (cart != null) {
                Set<String> requestedIdSet = new HashSet<>(requestedProductIds);
                for (CartItem ci : cartItemRepository.findAllByCartIdWithProduct(cart.getId())) {
                    if (requestedIdSet.contains(ci.getProduct().getId())) {
                        productIdsFromCart.add(ci.getProduct().getId());
                    }
                }
                if (!productIdsFromCart.isEmpty()) {
                    cartId = cart.getId();
                }
            }
        } else {
            Cart cart = cartRepository.findByMember_Id(buyer.getId())
                    .orElseThrow(() -> new BadRequestException("Giỏ hàng trống."));
            List<CartItem> cartItems = cartItemRepository.findAllByCartIdWithProduct(cart.getId());
            if (cartItems.isEmpty()) {
                throw new BadRequestException("Giỏ hàng trống.");
            }

            cartId = cart.getId();
            for (CartItem ci : cartItems) {
                String productId = ci.getProduct().getId();
                productIdsFromCart.add(productId);
                lockedProducts.add(lockAndValidateProduct(productId, buyer.getId()));
            }
        }

        int paymentTimeoutMinutes = systemConfigReader.getInt(
                SystemConfigKeys.ORDER_PAYMENT_TIMEOUT_MINUTES,
                SystemConfigKeys.DEFAULT_ORDER_PAYMENT_TIMEOUT_MINUTES);
        Instant paymentDeadline = Instant.now().plus(paymentTimeoutMinutes, ChronoUnit.MINUTES);

        List<BigDecimal> shippingFees = new ArrayList<>(lockedProducts.size());
        ShippingFeeMemo shippingFeeMemo = new ShippingFeeMemo(ghnShippingFeeCalculator);
        for (Product product : lockedProducts) {
            shippingFees.add(shippingFeeMemo.calculate(product.getWarehouse(), shippingAddress));
        }

        VoucherApplication voucherApplication =
                applyVoucher(request.getVoucherCode(), buyer, lockedProducts, shippingFees);

        List<OrderResponse> responses = new ArrayList<>();

        for (int i = 0; i < lockedProducts.size(); i++) {
            Product product = lockedProducts.get(i);
            Warehouse warehouse = product.getWarehouse();
            BigDecimal subtotal = product.getCurrentPrice();
            BigDecimal shippingFee = shippingFees.get(i);
            BigDecimal discount = voucherApplication.discountOf(product.getId());

            Order order = Order.builder()
                    .orderNo(generateOrderNo())
                    .buyer(buyer)
                    .warehouse(warehouse)
                    .shippingAddress(shippingAddress)
                    .shippingRecipientName(shippingAddress.getRecipientName())
                    .shippingPhone(shippingAddress.getPhone())
                    .shippingAddressDetail(shippingAddress.getAddressDetail())
                    .shippingWardName(shippingAddress.getWardName())
                    .shippingProvinceName(shippingAddress.getProvinceName())
                    .shippingIsNewAddress(true)
                    .subtotal(subtotal)
                    .shippingFee(shippingFee)
                    .voucher(voucherApplication.voucher())
                    .discountAmount(discount)
                    .totalAmount(subtotal.add(shippingFee).subtract(discount))
                    .paymentMethod(OrderPaymentMethod.WALLET)
                    .status(OrderStatus.PENDING_PAYMENT)
                    .paymentDeadline(paymentDeadline)
                    .buyerNote(request.getBuyerNote())
                    .build();
            orderRepository.save(order);
            auditLogger.logOperation(buyer, "CREATE_ORDER", "ORDER", order.getId(), order.getStatus().name());

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .priceAtOrder(product.getCurrentPrice())
                    .commissionRateApplied(commissionRateForTerm(product))
                    .taxRateApplied(taxRate)
                    .termApplied(product.getCurrentTerm())
                    .build();
            orderItemRepository.save(item);

            product.setStatus(ProductStatus.RESERVED);
            productRepository.save(product);

            responses.add(OrderResponse.builder()
                    .id(order.getId().toString())
                    .orderNo(order.getOrderNo())
                    .warehouseId(warehouse.getId().toString())
                    .shippingRecipientName(order.getShippingRecipientName())
                    .shippingPhone(order.getShippingPhone())
                    .shippingAddressDetail(order.getShippingAddressDetail())
                    .shippingWardName(order.getShippingWardName())
                    .shippingProvinceName(order.getShippingProvinceName())
                    .subtotal(order.getSubtotal())
                    .shippingFee(order.getShippingFee())
                    .discountAmount(order.getDiscountAmount())
                    .voucherCode(voucherApplication.code())
                    .voucherName(voucherApplication.name())
                    .totalAmount(order.getTotalAmount())
                    .status(order.getStatus().name())
                    .paymentDeadline(order.getPaymentDeadline())
                    .build());
        }

        if (cartId != null && !productIdsFromCart.isEmpty()) {
            cartItemRepository.deleteByCart_IdAndProduct_IdIn(cartId, new ArrayList<>(productIdsFromCart));
        }

        return responses;
    }

    @Override
    public OrderListResponse getOrders(String email, String status, int page, int size) {

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        List<OrderStatus> statuses = parseStatuses(status);

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Order> result = orderRepository.findByBuyer_IdAndStatusIn(buyer.getId(), statuses, pageable);

        List<UUID> orderIds = result.getContent().stream().map(Order::getId).toList();
        List<OrderItem> orderItems = orderItemRepository.findAllByOrder_IdInOrderByIdAsc(orderIds);

        Map<UUID, List<OrderItem>> itemsByOrderId = orderItems.stream()
                .collect(Collectors.groupingBy(oi -> oi.getOrder().getId(), LinkedHashMap::new, Collectors.toList()));

        List<String> firstProductIds = itemsByOrderId.values().stream()
                .filter(items -> !items.isEmpty())
                .map(items -> items.get(0).getProduct().getId())
                .distinct()
                .toList();

        Map<String, String> thumbnailByProductId = productPhotoRepository
                .findByProduct_IdInOrderByIdAsc(firstProductIds).stream()
                .collect(Collectors.toMap(
                        photo -> photo.getProduct().getId(),
                        ProductPhoto::getImageUrl,
                        (first, second) -> first
                ));

        Map<UUID, String> returnRequestIdByOrderId = new java.util.HashMap<>();
        if (!orderIds.isEmpty()) {
            for (Object[] row : returnRequestRepository.findOrderIdAndIdByOrderIds(orderIds)) {
                returnRequestIdByOrderId.putIfAbsent((UUID) row[0], row[1].toString());
            }
        }

        Page<OrderSummaryResponse> mapped = result.map(order -> {
            List<OrderItem> items = itemsByOrderId.getOrDefault(order.getId(), List.of());
            OrderItem firstItem = items.isEmpty() ? null : items.get(0);

            return OrderSummaryResponse.builder()
                    .id(order.getId().toString())
                    .orderNo(order.getOrderNo())
                    .status(order.getStatus().name())
                    .createdAt(order.getCreatedAt())
                    .expectedDeliveryDate(order.getExpectedDeliveryDate())
                    .refundAmount(order.getRefundAmount())
                    .returnRequestId(returnRequestIdByOrderId.get(order.getId()))
                    .totalAmount(order.getTotalAmount())
                    .warehouseName(order.getWarehouse().getName())
                    .itemCount(items.size())
                    .firstItemName(firstItem != null ? firstItem.getProduct().getName() : null)
                    .firstItemThumbnail(firstItem != null
                            ? thumbnailByProductId.get(firstItem.getProduct().getId()) : null)
                    .paymentDeadline(order.getPaymentDeadline())
                    .deliveredAt(order.getDeliveredAt())
                    .autoCompleteDeadline(order.getAutoCompleteDeadline())
                    .returnDeadline(returnDeadline(order))
                    .serviceRating(order.getServiceRating())
                    .build();
        });

        return OrderListResponse.builder()
                .orders(PageResponse.from(mapped))
                .statusCounts(buildStatusCounts(buyer.getId()))
                .build();
    }

    private List<OrderStatus> parseStatuses(String status) {
        if (!StringUtils.hasText(status)) {
            return List.of(OrderStatus.values());
        }

        List<OrderStatus> statuses = new ArrayList<>();
        for (String token : status.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                statuses.add(OrderStatus.valueOf(trimmed.toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("status không hợp lệ: " + trimmed);
            }
        }

        return statuses.isEmpty() ? List.of(OrderStatus.values()) : statuses;
    }

    private Map<String, Long> buildStatusCounts(UUID buyerId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s.name(), 0L);
        }
        for (Object[] row : orderRepository.countGroupedByStatusForBuyer(buyerId)) {
            counts.put(((OrderStatus) row[0]).name(), (Long) row[1]);
        }
        return counts;
    }

    @Override
    public SoldOrderListResponse getSoldOrders(String email, String status, int page, int size) {

        Account seller = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        List<OrderStatus> statuses = parseSoldStatuses(status);

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));

        Page<Order> result = orderRepository.findOrdersContainingSellerItems(
                seller.getId(), statuses, pageable);

        List<UUID> orderIds = result.getContent().stream().map(Order::getId).toList();

        List<OrderItem> sellerItems = orderIds.isEmpty()
                ? List.of()
                : orderItemRepository.findSellerItemsByOrderIds(orderIds, seller.getId());

        Map<UUID, List<OrderItem>> itemsByOrderId = sellerItems.stream()
                .collect(Collectors.groupingBy(oi -> oi.getOrder().getId(),
                        LinkedHashMap::new, Collectors.toList()));

        List<String> productIds = sellerItems.stream()
                .map(oi -> oi.getProduct().getId())
                .distinct()
                .toList();

        Map<String, String> thumbnailByProductId = productPhotoRepository
                .findByProduct_IdInOrderByIdAsc(productIds).stream()
                .collect(Collectors.toMap(
                        photo -> photo.getProduct().getId(),
                        ProductPhoto::getImageUrl,
                        (first, second) -> first
                ));

        Page<SoldOrderResponse> mapped = result.map(order -> {
            List<OrderItem> items = itemsByOrderId.getOrDefault(order.getId(), List.of());

            List<SoldOrderItemResponse> itemResponses = items.stream()
                    .map(item -> toSoldOrderItem(item, thumbnailByProductId))
                    .toList();

            BigDecimal subtotal = itemResponses.stream()
                    .map(SoldOrderItemResponse::getSalePrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal netAmount = itemResponses.stream()
                    .map(SoldOrderItemResponse::getNetAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return SoldOrderResponse.builder()
                    .orderId(order.getId().toString())
                    .orderNo(order.getOrderNo())
                    .status(order.getStatus().name())
                    .warehouseName(order.getWarehouse().getName())
                    .itemCount(itemResponses.size())
                    .subtotal(subtotal)
                    .netAmount(netAmount)
                    .paidOut(order.getStatus() == OrderStatus.COMPLETED)
                    .createdAt(order.getCreatedAt())
                    .deliveredAt(order.getDeliveredAt())
                    .completedAt(order.getCompletedAt())
                    .cancelledAt(order.getCancelledAt())
                    .items(itemResponses)
                    .build();
        });

        return SoldOrderListResponse.builder()
                .orders(PageResponse.from(mapped))
                .statusCounts(buildStatusCountsForSeller(seller.getId()))
                .build();
    }

    private SoldOrderItemResponse toSoldOrderItem(OrderItem item, Map<String, String> thumbnailByProductId) {
        BigDecimal salePrice = item.getPriceAtOrder();
        BigDecimal commission = salePrice.multiply(item.getCommissionRateApplied());

        return SoldOrderItemResponse.builder()
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .thumbnailUrl(thumbnailByProductId.get(item.getProduct().getId()))
                .salePrice(salePrice)
                .termApplied(item.getTermApplied())
                .commissionRate(item.getCommissionRateApplied())
                .commission(commission)
                .tax(salePrice.multiply(item.getTaxRateApplied()))
                .netAmount(salePrice.subtract(commission))
                .build();
    }

    private List<OrderStatus> parseSoldStatuses(String status) {
        if (!StringUtils.hasText(status)) {
            return List.of(OrderStatus.PAID, OrderStatus.PACKING, OrderStatus.PACKED, OrderStatus.SHIPPING,
                    OrderStatus.DELIVERED, OrderStatus.COMPLETED, OrderStatus.RETURNED);
        }
        return parseStatuses(status);
    }

    private Map<String, Long> buildStatusCountsForSeller(UUID sellerId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s.name(), 0L);
        }
        for (Object[] row : orderRepository.countGroupedByStatusForSeller(sellerId)) {
            counts.put(((OrderStatus) row[0]).name(), (Long) row[1]);
        }
        return counts;
    }

    @Override
    public WarehouseOrderListResponse getWarehouseSoldOrders(String email, String status, int page, int size) {

        Account moderator = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        if (moderator.getWarehouse() == null) {
            throw new ForbiddenException("Bạn chưa được phân công quản lý kho nào.");
        }
        UUID warehouseId = moderator.getWarehouse().getId();

        List<OrderStatus> statuses = parseSoldStatuses(status);

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Order> result = orderRepository.findByWarehouse_IdAndStatusIn(warehouseId, statuses, pageable);

        List<UUID> orderIds = result.getContent().stream().map(Order::getId).toList();
        List<OrderItem> orderItems = orderItemRepository.findAllByOrder_IdInOrderByIdAsc(orderIds);

        Map<UUID, List<OrderItem>> itemsByOrderId = orderItems.stream()
                .collect(Collectors.groupingBy(oi -> oi.getOrder().getId(), LinkedHashMap::new, Collectors.toList()));

        List<String> firstProductIds = itemsByOrderId.values().stream()
                .filter(items -> !items.isEmpty())
                .map(items -> items.get(0).getProduct().getId())
                .distinct()
                .toList();

        Map<String, String> thumbnailByProductId = productPhotoRepository
                .findByProduct_IdInOrderByIdAsc(firstProductIds).stream()
                .collect(Collectors.toMap(
                        photo -> photo.getProduct().getId(),
                        ProductPhoto::getImageUrl,
                        (first, second) -> first
                ));

        Page<WarehouseOrderResponse> mapped = result.map(order -> {
            List<OrderItem> items = itemsByOrderId.getOrDefault(order.getId(), List.of());
            OrderItem firstItem = items.isEmpty() ? null : items.get(0);

            return WarehouseOrderResponse.builder()
                    .id(order.getId().toString())
                    .orderNo(order.getOrderNo())
                    .status(order.getStatus().name())
                    .createdAt(order.getCreatedAt())
                    .totalAmount(order.getTotalAmount())
                    .itemCount(items.size())
                    .firstItemName(firstItem != null ? firstItem.getProduct().getName() : null)
                    .firstItemThumbnail(firstItem != null
                            ? thumbnailByProductId.get(firstItem.getProduct().getId()) : null)
                    .buyerName(order.getShippingRecipientName())
                    .buyerPhone(order.getShippingPhone())
                    .scannedCount(order.getScannedCount() == null ? 0 : order.getScannedCount())
                    .totalCount(items.size())
                    .ghnTrackingCode(order.getGhnTrackingCode())
                    .deliveredAt(order.getDeliveredAt())
                    .completedAt(order.getCompletedAt())
                    .cancelledAt(order.getCancelledAt())
                    .build();
        });

        return WarehouseOrderListResponse.builder()
                .orders(PageResponse.from(mapped))
                .statusCounts(buildStatusCountsForWarehouse(warehouseId))
                .build();
    }

    private Map<String, Long> buildStatusCountsForWarehouse(UUID warehouseId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s.name(), 0L);
        }
        for (Object[] row : orderRepository.countGroupedByStatusForWarehouse(warehouseId)) {
            counts.put(((OrderStatus) row[0]).name(), (Long) row[1]);
        }
        return counts;
    }

    @Override
    public OrderDetailResponse getOrderDetail(String email, UUID orderId) {

        Account caller = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        authorizeOrderDetailAccess(caller, order);

        List<OrderItem> orderItems = orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(orderId);

        List<String> productIds = orderItems.stream().map(oi -> oi.getProduct().getId()).distinct().toList();
        Map<String, String> thumbnailByProductId = productPhotoRepository
                .findByProduct_IdInOrderByIdAsc(productIds).stream()
                .collect(Collectors.toMap(
                        photo -> photo.getProduct().getId(),
                        ProductPhoto::getImageUrl,
                        (first, second) -> first
                ));

        List<OrderDetailItemResponse> items = orderItems.stream()
                .map(oi -> {
                    Product product = oi.getProduct();
                    return OrderDetailItemResponse.builder()
                            .productId(product.getId())
                            .name(product.getName())
                            .thumbnailUrl(thumbnailByProductId.get(product.getId()))
                            .size(product.getSize())
                            .brandName(product.getBrand().getName())
                            .condition(product.getConditionNote())
                            .priceAtOrder(oi.getPriceAtOrder())
                            .scanned(oi.getScanned())
                            .build();
                })
                .toList();

        Instant paidAt = paymentRepository.findByOrder_Id(orderId)
                .map(Payment::getPaidAt)
                .orElse(null);

        String returnRequestId = returnRequestRepository
                .findFirstByOrder_IdOrderByCreatedAtDesc(orderId)
                .map(returnRequest -> returnRequest.getId().toString())
                .orElse(null);

        List<String> scannedProductIds = orderItems.stream()
                .filter(oi -> Boolean.TRUE.equals(oi.getScanned()))
                .map(oi -> oi.getProduct().getId())
                .toList();

        int totalCount = orderItems.size();
        int scannedCount = order.getScannedCount() == null ? 0 : order.getScannedCount();

        String claimedBy = queueItemRepository.findByTypeAndRefId(QueueType.PACKING, orderId.toString())
                .map(QueueItem::getClaimedBy)
                .map(account -> account.getId().toString())
                .orElse(null);

        List<String> allowedActions = packingAllowedActions(order, scannedCount, totalCount);

        return OrderDetailResponse.builder()
                .handedOverAt(order.getHandedOverAt())
                .packingPhotoUrls(order.getPackingPhotoUrls() == null ? List.of() : List.copyOf(order.getPackingPhotoUrls()))
                .id(order.getId().toString())
                .orderNo(order.getOrderNo())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .paymentMethod(order.getPaymentMethod().name())
                .paidAt(paidAt)
                .paymentDeadline(order.getPaymentDeadline())
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .discountAmount(nullToZero(order.getDiscountAmount()))
                .voucherCode(order.getVoucher() == null ? null : order.getVoucher().getCode())
                .voucherName(order.getVoucher() == null
                        ? null : order.getVoucher().getVoucherTemplate().getName())
                .totalAmount(order.getTotalAmount())
                .warehouseId(order.getWarehouse().getId().toString())
                .warehouseName(order.getWarehouse().getName())
                .returnRequestId(returnRequestId)
                .returnDeadline(returnDeadline(order))
                .shippingRecipientName(order.getShippingRecipientName())
                .shippingPhone(order.getShippingPhone())
                .shippingAddressDetail(order.getShippingAddressDetail())
                .shippingWardName(order.getShippingWardName())
                .shippingProvinceName(order.getShippingProvinceName())
                .items(items)
                .ghnTrackingCode(order.getGhnTrackingCode())
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .shipmentCreatedAt(order.getShipmentCreatedAt())
                .deliveredAt(order.getDeliveredAt())
                .autoCompleteDeadline(order.getAutoCompleteDeadline())
                .serviceRating(order.getServiceRating())
                .ratedAt(order.getRatedAt())
                .cancelReason(order.getCancelReason() != null ? order.getCancelReason().name() : null)
                .cancelledAt(order.getCancelledAt())
                .refundAmount(order.getRefundAmount())
                .addressChangedAt(order.getAddressChangedAt())
                .completedAt(order.getCompletedAt())
                .ratingComment(order.getRatingComment())
                .ratingImageUrls(order.getRatingImageUrls())
                .scannedProductIds(scannedProductIds)
                .scannedCount(scannedCount)
                .totalCount(totalCount)
                .allowedActions(allowedActions)
                .claimedBy(claimedBy)
                .buyerNote(order.getBuyerNote())
                .build();
    }

    private void authorizeOrderDetailAccess(Account caller, Order order) {
        switch (caller.getRole()) {
            case ADMIN -> {
            }
            case MODERATOR -> {
                if (caller.getWarehouse() == null
                        || !order.getWarehouse().getId().equals(caller.getWarehouse().getId())) {
                    throw new ForbiddenException("Bạn không có quyền xem đơn này.");
                }
            }
            case MEMBER -> {
                if (!order.getBuyer().getId().equals(caller.getId())) {
                    throw new ForbiddenException("Bạn không có quyền xem đơn này.");
                }
            }
            default -> throw new ForbiddenException("Bạn không có quyền xem đơn này.");
        }
    }

    private List<String> packingAllowedActions(Order order, int scannedCount, int totalCount) {
        List<String> allowedActions = new ArrayList<>();
        if (order.getStatus() == OrderStatus.PACKED) {
            return List.of("HANDOVER");
        }
        boolean packableStatus = order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.PACKING;
        if (!packableStatus || totalCount == 0) {
            return allowedActions;
        }
        if (scannedCount < totalCount) {
            allowedActions.add("SCAN");
        } else if (!StringUtils.hasText(order.getGhnTrackingCode())) {
            allowedActions.add("CREATE_SHIPMENT");
        }
        return allowedActions;
    }

    private Instant returnDeadline(Order order) {
        if (order.getReturnDeadline() != null) {
            return order.getReturnDeadline();
        }
        if (order.getDeliveredAt() == null) {
            return null;
        }
        int windowDays = systemConfigReader.getInt(
                SystemConfigKeys.RETURN_REQUEST_WINDOW_DAYS,
                SystemConfigKeys.DEFAULT_RETURN_REQUEST_WINDOW_DAYS);
        return order.getDeliveredAt().plus(windowDays, ChronoUnit.DAYS);
    }

    @Override
    @Transactional
    public OrderCancelResponse cancelOrder(String email, UUID orderId) {

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền hủy đơn này.");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ConflictException("Đơn đã hủy trước đó.", "ORDER_ALREADY_CANCELLED");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException("Đơn đã đóng gói, không thể hủy.", "CANNOT_CANCEL_AFTER_PACKING");
        }

        releaseReservedProducts(orderId);

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReason(CancelReason.BUYER_CANCELLED);
            businessNotifications.order(order, "Đơn hàng đã huỷ", "đã huỷ theo yêu cầu của bạn.");
            order.setCancelledAt(Instant.now());
            orderRepository.save(order);
            releaseVoucherIfAllOrdersCancelled(order);
        } else {
            refundBuyerAndCancel(order, CancelReason.BUYER_CANCELLED, "Hoàn tiền hủy đơn " + order.getOrderNo());
        }

        auditLogger.logOperation(buyer, "CANCEL_ORDER", "ORDER", orderId, "CANCELLED");
        return OrderCancelResponse.builder()
                .id(order.getId().toString())
                .status(order.getStatus().name())
                .refundAmount(order.getRefundAmount())
                .build();
    }

    @Override
    @Transactional
    public OrderResponse updateShippingAddress(String email, UUID orderId, UpdateOrderShippingAddressRequest request) {

        if (request == null || !StringUtils.hasText(request.getShippingAddressId())) {
            throw new BadRequestException("shippingAddressId là bắt buộc.");
        }

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền đổi địa chỉ của đơn này.");
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException(
                    "Đơn đã đóng gói, không thể đổi địa chỉ giao hàng.", "CANNOT_CHANGE_ADDRESS_AFTER_PACKING");
        }

        if (order.getAddressChangedAt() != null) {
            throw new ConflictException(
                    "Địa chỉ giao hàng của đơn này đã được đổi trước đó.", "ADDRESS_ALREADY_CHANGED");
        }

        UUID shippingAddressId;
        try {
            shippingAddressId = UUID.fromString(request.getShippingAddressId());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("shippingAddressId không hợp lệ.");
        }

        ShippingAddress newAddress = shippingAddressRepository.findById(shippingAddressId)
                .filter(a -> a.getMember().getId().equals(buyer.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ."));

        if (!StringUtils.hasText(newAddress.getWardName()) || !StringUtils.hasText(newAddress.getProvinceName())) {
            throw new BadRequestException("Địa chỉ chưa có đầy đủ thông tin phường/xã, tỉnh/thành.");
        }

        BigDecimal newFee = ghnShippingFeeCalculator.calculate(order.getWarehouse(), newAddress);
        BigDecimal oldFee = order.getShippingFee();

        BigDecimal refundedAmount = BigDecimal.ZERO;

        if (order.getStatus() == OrderStatus.PAID && newFee.compareTo(oldFee) > 0) {
            throw new ConflictException(
                    "Phí vận chuyển tới địa chỉ mới tăng thêm, phí mới là " + newFee
                            + "đ, vui lòng chọn địa chỉ khác hoặc giữ địa chỉ cũ.",
                    "SHIPPING_FEE_INCREASED");
        }

        BigDecimal feeDiff = oldFee.subtract(newFee);
        if (order.getStatus() == OrderStatus.PAID && feeDiff.signum() > 0) {
            refundedAmount = feeDiff;
            refundShippingFeeDifference(order, refundedAmount);
        }

        order.setShippingAddress(newAddress);
        order.setShippingRecipientName(newAddress.getRecipientName());
        order.setShippingPhone(newAddress.getPhone());
        order.setShippingAddressDetail(newAddress.getAddressDetail());
        order.setShippingWardName(newAddress.getWardName());
        order.setShippingProvinceName(newAddress.getProvinceName());
        order.setShippingFee(newFee);
        order.setTotalAmount(order.getSubtotal().add(newFee).subtract(nullToZero(order.getDiscountAmount())));
        order.setAddressChangedAt(Instant.now());
        orderRepository.save(order);

        return OrderResponse.builder()
                .id(order.getId().toString())
                .orderNo(order.getOrderNo())
                .warehouseId(order.getWarehouse().getId().toString())
                .shippingRecipientName(order.getShippingRecipientName())
                .shippingPhone(order.getShippingPhone())
                .shippingAddressDetail(order.getShippingAddressDetail())
                .shippingWardName(order.getShippingWardName())
                .shippingProvinceName(order.getShippingProvinceName())
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .paymentDeadline(order.getPaymentDeadline())
                .addressChangedAt(order.getAddressChangedAt())
                .refundedAmount(refundedAmount)
                .build();
    }

    private void refundShippingFeeDifference(Order order, BigDecimal amount) {
        Wallet wallet = walletRepository.findByMemberIdForUpdate(order.getBuyer().getId())
                .orElseGet(() -> getOrCreateWallet(order.getBuyer()));
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction refundTransaction = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.REFUND)
                .amount(amount)
                .balanceAfter(wallet.getBalance())
                .relatedOrder(order)
                .status(TransactionStatus.SUCCESS)
                .note("Hoàn phần phí vận chuyển giảm khi đổi địa chỉ đơn " + order.getOrderNo())
                .build();
        walletTransactionRepository.save(refundTransaction);
        recordPlatformCounterpart(refundTransaction);
    }

    @Override
    @Transactional
    public PayOrdersResponse payOrders(String email, PayOrdersRequest request) {

        if (request == null || request.getOrderIds() == null || request.getOrderIds().isEmpty()) {
            throw new BadRequestException("orderIds là bắt buộc.");
        }

        OrderPaymentMethod method = parsePaymentMethod(request.getMethod());
        if (method != OrderPaymentMethod.WALLET) {
            throw new BadRequestException(
                    "method chỉ nhận WALLET ở endpoint này. Trả bằng VNPay dùng "
                            + "POST /api/vnpay/create.");
        }

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        List<Order> orders = loadPayableOrders(email, parseOrderIds(request.getOrderIds()));

        Wallet wallet = walletRepository.findByMemberIdForUpdate(buyer.getId())
                .orElseThrow(() -> new ConflictException(
                        "INSUFFICIENT_BALANCE: ví chưa có số dư.", "INSUFFICIENT_BALANCE"));

        BigDecimal total = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal available = wallet.getBalance().subtract(wallet.getLockedBalance());
        if (total.compareTo(available) > 0) {
            throw new ConflictException(
                    "INSUFFICIENT_BALANCE: ví không đủ số dư khả dụng để thanh toán.", "INSUFFICIENT_BALANCE");
        }

        List<PaidOrderResponse> paidOrders = new ArrayList<>();
        for (Order order : orders) {
            wallet.setBalance(wallet.getBalance().subtract(order.getTotalAmount()));
            walletRepository.save(wallet);

            WalletTransaction paymentTransaction = WalletTransaction.builder()
                    .transactionNo(generateTransactionNo())
                    .walletType(WalletType.MEMBER)
                    .walletId(wallet.getId())
                    .type(WalletTransactionType.ORDER_PAYMENT)
                    .amount(order.getTotalAmount().negate())
                    .balanceAfter(wallet.getBalance())
                    .relatedOrder(order)
                    .status(TransactionStatus.SUCCESS)
                    .note("Thanh toán bằng ví cho đơn " + order.getOrderNo())
                    .build();
            walletTransactionRepository.save(paymentTransaction);
            recordPlatformCounterpart(paymentTransaction);

            order.setPaymentMethod(OrderPaymentMethod.WALLET);
            order.setStatus(OrderStatus.PAID);
            businessNotifications.order(order, "Thanh toán thành công", "đã thanh toán thành công, đang chờ đóng gói.");
            orderRepository.save(order);
            queueService.enqueue(QueueType.PACKING, order.getId().toString(), order.getWarehouse());

            auditLogger.logOperation(buyer, "PAY_ORDER_WALLET", "ORDER", order.getId(), "PAID");
            paidOrders.add(PaidOrderResponse.builder()
                    .id(order.getId().toString())
                    .orderNo(order.getOrderNo())
                    .status(order.getStatus().name())
                    .totalAmount(order.getTotalAmount())
                    .build());
        }

        return PayOrdersResponse.builder()
                .orders(paidOrders)
                .totalAmount(total)
                .walletBalance(wallet.getBalance())
                .build();
    }

    @Override
    public List<Order> loadPayableOrders(String email, List<UUID> orderIds) {

        if (orderIds == null || orderIds.isEmpty()) {
            throw new BadRequestException("orderIds là bắt buộc.");
        }

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        List<Order> orders = new ArrayList<>();
        for (UUID orderId : orderIds) {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

            if (!order.getBuyer().getId().equals(buyer.getId())) {
                throw new ForbiddenException("Đơn hàng này không thuộc về bạn.");
            }
            if (order.getStatus() == OrderStatus.PAID) {
                throw new ConflictException(
                        "Đơn " + order.getOrderNo() + " đã được thanh toán.", "ORDER_ALREADY_PAID");
            }
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new ConflictException(
                        "Đơn " + order.getOrderNo() + " không ở trạng thái chờ thanh toán.", "ORDER_NOT_PENDING");
            }

            orders.add(order);
        }

        return orders;
    }

    @Override
    @Transactional
    public void applyGatewayPaymentSuccess(UUID orderId, OrderPaymentMethod method, String note) {

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn(">>>> Ket qua thanh toan thanh cong cho orderId={} khong con don tuong ung", orderId);
            return;
        }

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            recordGatewayPaymentReceived(order, "Thanh toán đơn hàng " + order.getOrderNo() + " qua " + method);
            order.setPaymentMethod(method);
            order.setStatus(OrderStatus.PAID);
            businessNotifications.order(order, "Thanh toán thành công", "đã thanh toán thành công, đang chờ đóng gói.");
            orderRepository.save(order);
            queueService.enqueue(QueueType.PACKING, order.getId().toString(), order.getWarehouse());
            auditLogger.logSystemOperation("CONFIRM_ORDER_PAYMENT", "ORDER", orderId, "PAID", method.name());
            return;
        }

        if (order.getPaymentMethod() == method && order.getStatus() != OrderStatus.CANCELLED) {
            return;
        }

        if (order.getRefundAmount() != null) {
            log.warn(">>>> Don {} da duoc hoan tien truoc do, bo qua ket qua thanh toan muon tu {}",
                    order.getOrderNo(), method);
            return;
        }

        recordGatewayPaymentReceived(order, "Tiền " + method + " về muộn cho đơn " + order.getOrderNo());
        refundBuyerWallet(order, note);
        order.setRefundAmount(order.getTotalAmount());
        orderRepository.save(order);

        auditLogger.logSystemOperation("REFUND_LATE_ORDER_PAYMENT", "ORDER", orderId, "REFUNDED", method.name());
        log.warn(">>>> Tien {} ve muon cho don {} dang o trang thai {} - da hoan {} vao vi nguoi mua",
                method, order.getOrderNo(), order.getStatus(), order.getTotalAmount());
    }

    private static OrderPaymentMethod parsePaymentMethod(String method) {
        if (!StringUtils.hasText(method)) {
            throw new BadRequestException("method là bắt buộc.");
        }
        try {
            return OrderPaymentMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("method không hợp lệ: " + method);
        }
    }

    private static List<UUID> parseOrderIds(List<String> rawOrderIds) {
        List<UUID> orderIds = new ArrayList<>();
        for (String rawOrderId : rawOrderIds) {
            if (!StringUtils.hasText(rawOrderId)) {
                throw new BadRequestException("orderIds chứa giá trị rỗng.");
            }
            UUID orderId;
            try {
                orderId = UUID.fromString(rawOrderId.trim());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("orderId không hợp lệ: " + rawOrderId);
            }
            if (!orderIds.contains(orderId)) {
                orderIds.add(orderId);
            }
        }
        return orderIds;
    }

    @Override
    @Transactional
    public OrderConfirmReceiptResponse confirmReceipt(String email, UUID orderId) {

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền xác nhận đơn này.");
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ConflictException(
                    "Đơn chưa ở trạng thái đã giao, không thể xác nhận nhận hàng.",
                    "ORDER_NOT_DELIVERED_YET");
        }

        if (returnRequestRepository.existsByOrder_IdAndStatusIn(orderId, OPEN_RETURN_STATUSES)) {
            throw new ConflictException(
                    "Đơn đang có yêu cầu trả hàng chưa xử lý xong.",
                    "RETURN_REQUEST_IN_PROGRESS");
        }

        completeOrder(order);

        return OrderConfirmReceiptResponse.builder()
                .id(order.getId().toString())
                .status(order.getStatus().name())
                .build();
    }

    private void completeOrder(Order order) {
        for (OrderItem item : orderItemRepository.findAllByOrder_Id(order.getId())) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId()).orElse(null);
            if (product != null && product.getStatus() == ProductStatus.RESERVED) {
                product.setStatus(ProductStatus.SOLD);
                businessNotifications.product(product, NotificationType.ORDER_UPDATE, "Món ký gửi đã được bán",
                        "đã được bán trong đơn " + order.getOrderNo() + ". Tiền sẽ vào ví sau thời hạn trả hàng.");
                productRepository.save(product);
            }
        }

        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        businessNotifications.order(order, "Đơn hàng đã hoàn thành", "đã hoàn thành. Cảm ơn bạn đã sử dụng ReHome.");
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void autoCompleteOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            return;
        }

        if (returnRequestRepository.existsByOrder_IdAndStatusIn(orderId, OPEN_RETURN_STATUSES)) {
            return;
        }

        completeOrder(order);
    }

    @Override
    @Transactional
    public void payoutOrder(UUID orderId, String note) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (order.getPayoutAt() != null) {
            return;
        }

        for (OrderItem item : orderItemRepository.findAllByOrder_Id(orderId)) {
            BigDecimal payoutAmount = item.getPriceAtOrder()
                    .subtract(item.getPriceAtOrder().multiply(item.getCommissionRateApplied()));

            Account seller = item.getProduct().getMember();
            Wallet sellerWallet = getOrCreateWallet(seller);
            sellerWallet.setBalance(sellerWallet.getBalance().add(payoutAmount));
            walletRepository.save(sellerWallet);

            WalletTransaction payoutTransaction = WalletTransaction.builder()
                    .transactionNo(generateTransactionNo())
                    .walletType(WalletType.MEMBER)
                    .walletId(sellerWallet.getId())
                    .type(WalletTransactionType.ORDER_PAYOUT)
                    .amount(payoutAmount)
                    .balanceAfter(sellerWallet.getBalance())
                    .relatedOrder(order)
                    .relatedOrderItem(item)
                    .status(TransactionStatus.SUCCESS)
                    .note(note)
                    .build();
            walletTransactionRepository.save(payoutTransaction);
            recordPlatformCounterpart(payoutTransaction);
            businessNotifications.product(item.getProduct(), NotificationType.WALLET_EVENT, "Tiền bán hàng đã vào ví",
                    "tiền bán hàng " + NotificationFormatting.vnd(payoutAmount) + " của đơn " + order.getOrderNo() + " đã vào ví.");

            recordPlatformRevenue(WalletTransactionType.COMMISSION_REVENUE,
                    item.getPriceAtOrder().subtract(payoutAmount), order, item,
                    "Hoa hồng đơn " + order.getOrderNo());
        }

        BigDecimal discount = nullToZero(order.getDiscountAmount());
        if (discount.signum() > 0) {
            recordPlatformPromotionCost(discount, order,
                    "Khuyến mãi đơn " + order.getOrderNo()
                            + (order.getVoucher() == null ? "" : " (" + order.getVoucher().getCode() + ")"));
        }

        order.setPayoutAt(Instant.now());
        orderRepository.save(order);

        pointsService.awardForOrder(order);
    }

    @Override
    @Transactional
    public OrderRatingResponse rateOrder(String email, UUID orderId, RateOrderRequest request) {

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền đánh giá đơn này.");
        }

        if (request.getServiceRating() == null
                || request.getServiceRating() < 1 || request.getServiceRating() > 5) {
            throw new BadRequestException("serviceRating phải từ 1 đến 5.");
        }

        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new ConflictException("Chỉ đánh giá đơn đã hoàn tất.", "ORDER_NOT_COMPLETED");
        }

        List<String> imageUrls = request.getImageUrls() == null ? List.of() : request.getImageUrls();
        CloudinaryImageUrls.requireOwnUrls(imageUrls, "imageUrls chỉ được chứa ảnh đã upload qua hệ thống.");

        order.setServiceRating(request.getServiceRating());
        moderation.check(order.getBuyer().getId(), "ORDER_RATING", request.getComment());
        order.setRatingComment(request.getComment());
        order.setRatingImageUrls(imageUrls);
        order.setRatedAt(Instant.now());
        orderRepository.save(order);

        return OrderRatingResponse.builder()
                .id(order.getId().toString())
                .serviceRating(order.getServiceRating())
                .comment(order.getRatingComment())
                .build();
    }

    @Override
    @Transactional
    public PackScanResponse packScan(String email, UUID orderId, PackScanRequest request) {

        Account moderator = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (moderator.getWarehouse() == null
                || !order.getWarehouse().getId().equals(moderator.getWarehouse().getId())) {
            throw new ForbiddenException("Bạn không có quyền quét đơn này.");
        }

        if (!StringUtils.hasText(request.getProductId())) {
            throw new BadRequestException("productId là bắt buộc.");
        }

        requireActiveClaim(moderator, orderId);
        requirePackable(order);

        List<OrderItem> items = orderItemRepository.findAllByOrder_Id(orderId);
        int totalCount = items.size();

        Optional<OrderItem> matchedItem = items.stream()
                .filter(i -> i.getProduct().getId().equals(request.getProductId()))
                .findFirst();

        if (matchedItem.isEmpty()) {
            return wrongSkuResponse(request.getProductId(), order.getScannedCount(), totalCount, moderator);
        }

        OrderItem item = matchedItem.get();
        if (Boolean.TRUE.equals(item.getScanned())) {
            return PackScanResponse.builder()
                    .matched(true)
                    .scannedCount(order.getScannedCount())
                    .totalCount(totalCount)
                    .scannedProductId(item.getProduct().getId())
                    .build();
        }

        item.setScanned(true);
        orderItemRepository.save(item);

        int newScannedCount = (order.getScannedCount() == null ? 0 : order.getScannedCount()) + 1;
        order.setScannedCount(newScannedCount);
        if (order.getStatus() == OrderStatus.PAID) {
            order.setStatus(OrderStatus.PACKING);
            businessNotifications.order(order, "Đơn hàng đang đóng gói", "kho đang đóng gói kiện hàng.");
        }
        orderRepository.save(order);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("label", order.getOrderNo());
        detail.put("productId", item.getProduct().getId());
        detail.put("scannedCount", newScannedCount);
        detail.put("totalCount", totalCount);
        auditLogger.logWarehouse(moderator, order.getWarehouse(), WarehouseAuditGroup.PACKING, "SCAN_SKU",
                "ORDER", order.getId().toString(), "SUCCESS", detail);

        return PackScanResponse.builder()
                .matched(true)
                .scannedCount(newScannedCount)
                .totalCount(totalCount)
                .scannedProductId(item.getProduct().getId())
                .build();
    }

    private void requireActiveClaim(Account moderator, UUID orderId) {
        QueueItem queueItem = queueItemRepository.findByTypeAndRefIdForUpdate(QueueType.PACKING, orderId.toString())
                .orElseThrow(() -> new AppException(ErrorCode.QUEUE_ITEM_NOT_FOUND));
        if (queueItem.getClaimedBy() == null || !queueItem.getClaimedBy().getId().equals(moderator.getId())) {
            throw new AppException(ErrorCode.QUEUE_ITEM_NOT_CLAIMED_BY_YOU);
        }
    }

    private void requirePackable(Order order) {
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.PACKING) {
            throw new ConflictException("Đơn không ở trạng thái đóng gói.", "ORDER_NOT_PACKABLE");
        }
        if (StringUtils.hasText(order.getGhnTrackingCode())) {
            throw new ConflictException("Đơn đã có vận đơn.", "SHIPMENT_ALREADY_CREATED");
        }
    }

    private PackScanResponse wrongSkuResponse(String productId, int scannedCount, int totalCount, Account moderator) {
        Product product = productRepository.findById(productId).orElse(null);

        if (product == null) {
            throw new BadRequestException("Không tìm thấy sản phẩm với mã này.");
        }

        if (!product.getWarehouse().getId().equals(moderator.getWarehouse().getId())) {
            return PackScanResponse.builder()
                    .matched(false)
                    .scannedCount(scannedCount)
                    .totalCount(totalCount)
                    .wrongItem(WrongItemDetail.builder()
                            .instructions("Sản phẩm không thuộc kho của bạn, không được xử lý tại đây.")
                            .build())
                    .build();
        }

        String belongsToOrderNo = orderItemRepository.findActiveByProductIds(List.of(productId)).stream()
                .findFirst()
                .map(oi -> oi.getOrder().getOrderNo())
                .orElse(null);

        String instructions = "Sản phẩm không thuộc đơn này. Vui lòng đặt lại đúng vị trí trong kho.";

        WrongItemDetail wrongItem = WrongItemDetail.builder()
                .productId(product.getId())
                .name(product.getName())
                .brandName(product.getBrand().getName())
                .size(product.getSize())
                .belongsToOrderNo(belongsToOrderNo)
                .instructions(instructions)
                .build();

        return PackScanResponse.builder()
                .matched(false)
                .scannedCount(scannedCount)
                .totalCount(totalCount)
                .wrongItem(wrongItem)
                .build();
    }

    @Override
    @Transactional
    public java.util.List<String> updatePackingPhotos(String email, UUID orderId,
            com.rehome.rehome.dto.request.UpdatePackingPhotosRequest request) {
        Account moderator = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (moderator.getRole() != com.rehome.rehome.entity.enums.Role.MODERATOR
                || moderator.getWarehouse() == null
                || !moderator.getWarehouse().getId().equals(order.getWarehouse().getId())) {
            throw new ForbiddenException("Only the assigned warehouse moderator can update packing photos.");
        }
        requireActiveClaim(moderator, orderId);
        requirePackable(order);
        if (request == null || request.photoUrls() == null || request.photoUrls().size() > 5
                || request.photoUrls().stream().anyMatch(url -> url == null || url.isBlank()
                        || url.length() > 2048 || !url.matches("https://[^\\s/]+(?:/[^\\s]*)?"))) {
            throw new BadRequestException("photoUrls must contain at most 5 HTTPS image URLs.");
        }
        if (order.getPackingPhotoUrls() == null) {
            order.setPackingPhotoUrls(new java.util.ArrayList<>());
        }
        var detail = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        detail.putPOJO("oldPhotoUrls", List.copyOf(order.getPackingPhotoUrls()));
        order.getPackingPhotoUrls().clear();
        order.getPackingPhotoUrls().addAll(request.photoUrls());
        detail.putPOJO("newPhotoUrls", List.copyOf(order.getPackingPhotoUrls()));
        orderRepository.save(order);
        auditLogger.logWarehouse(moderator, order.getWarehouse(), WarehouseAuditGroup.PACKING,
                "UPDATE_PACKING_PHOTOS", "ORDER", orderId.toString(), "SUCCESS", detail);
        return List.copyOf(order.getPackingPhotoUrls());
    }

    @Override
    @Transactional
    public CreateShipmentResponse createShipment(String email, UUID orderId) {

        Account moderator = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (moderator.getWarehouse() == null
                || !order.getWarehouse().getId().equals(moderator.getWarehouse().getId())) {
            throw new ForbiddenException("Bạn không có quyền tạo vận đơn cho đơn này.");
        }

        if (StringUtils.hasText(order.getGhnTrackingCode())) {
            return CreateShipmentResponse.builder()
                    .id(order.getId().toString())
                    .status(order.getStatus().name())
                    .ghnTrackingCode(order.getGhnTrackingCode())
                    .build();
        }

        requireActiveClaim(moderator, orderId);
        requirePackable(order);

        List<OrderItem> items = orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(orderId);
        int totalCount = items.size();

        if (totalCount == 0) {
            throw new ConflictException("Đơn không có món nào để đóng gói.", "ORDER_HAS_NO_ITEMS");
        }

        long scannedItems = items.stream().filter(i -> Boolean.TRUE.equals(i.getScanned())).count();
        if (order.getScannedCount() == null || order.getScannedCount() < totalCount || scannedItems < totalCount) {
            throw new ConflictException("Chưa quét đủ tất cả món trong đơn.", "ORDER_NOT_FULLY_SCANNED");
        }

        GhnCreateOrderRequest ghnRequest = buildGhnCreateOrderRequest(order, items);

        GhnCreateOrderResponse ghnResponse;
        try {
            ghnResponse = ghnApi.createOrder(ghnToken, ghnShopId, ghnRequest);
        } catch (Exception ex) {
            log.error(">>>> Loi goi GHN tao van don cho orderNo={}: {}", order.getOrderNo(), ex.getMessage());
            logGhnAddressBlocks(order, ghnRequest);
            throw new GhnException("GHN_ERROR: không thể tạo vận đơn, vui lòng thử lại.");
        }

        if (ghnResponse == null || ghnResponse.getData() == null
                || !StringUtils.hasText(ghnResponse.getData().getOrderCode())) {
            String ghnMessage = ghnResponse != null ? ghnResponse.getMessage() : null;
            log.error(">>>> GHN khong tra ve order_code cho orderNo={}: {}", order.getOrderNo(), ghnMessage);
            logGhnAddressBlocks(order, ghnRequest);
            throw new GhnException("GHN_ERROR: không thể tạo vận đơn, vui lòng thử lại.");
        }

        order.setGhnTrackingCode(ghnResponse.getData().getOrderCode());
        order.setExpectedDeliveryDate(parseGhnTime(ghnResponse.getData().getExpectedDeliveryTime()));
        order.setShipmentCreatedAt(Instant.now());
        order.setStatus(OrderStatus.PACKED);
        businessNotifications.order(order, "Đơn hàng đã đóng gói", "đã đóng gói · Mã GHN " + order.getGhnTrackingCode() + ".");
        orderRepository.save(order);
        queueService.dequeue(QueueType.PACKING, order.getId().toString());

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("label", order.getOrderNo());
        detail.put("ghnTrackingCode", order.getGhnTrackingCode());
        auditLogger.logWarehouse(moderator, order.getWarehouse(), WarehouseAuditGroup.PACKING, "CREATE_SHIPMENT",
                "ORDER", order.getId().toString(), "SUCCESS", detail);

        return CreateShipmentResponse.builder()
                .id(order.getId().toString())
                .status(order.getStatus().name())
                .ghnTrackingCode(order.getGhnTrackingCode())
                .build();
    }

    @Override
    @Transactional
    public OrderResponse handoverToCarrier(String email, UUID orderId) {
        Account moderator = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (moderator.getRole() != com.rehome.rehome.entity.enums.Role.MODERATOR
                || moderator.getWarehouse() == null
                || !moderator.getWarehouse().getId().equals(order.getWarehouse().getId())) {
            throw new ForbiddenException("Only the assigned warehouse moderator can hand over this order.");
        }
        if (order.getStatus() != OrderStatus.SHIPPING) {
            if (order.getStatus() != OrderStatus.PACKED) {
                throw new ConflictException("Order must be PACKED before handover.", "ORDER_NOT_PACKED");
            }
            order.setStatus(OrderStatus.SHIPPING);
            order.setHandedOverAt(Instant.now());
            businessNotifications.order(order, "Đơn hàng đang được giao", "đang được giao · Mã GHN " + order.getGhnTrackingCode() + ".");
            orderRepository.save(order);
            auditLogger.logWarehouseOp(moderator, order.getWarehouse(), WarehouseAuditGroup.PACKING,
                    "HANDOVER_ORDER", "ORDER", orderId.toString(), "SHIPPING", order.getOrderNo());
        }
        return OrderResponse.builder().id(orderId.toString()).orderNo(order.getOrderNo())
                .warehouseId(order.getWarehouse().getId().toString())
                .shippingRecipientName(order.getShippingRecipientName()).shippingPhone(order.getShippingPhone())
                .shippingAddressDetail(order.getShippingAddressDetail()).shippingWardName(order.getShippingWardName())
                .shippingProvinceName(order.getShippingProvinceName()).subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee()).discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount()).status(order.getStatus().name())
                .handedOverAt(order.getHandedOverAt()).build();
    }

    @Override
    public ShipmentLabelResponse getShipmentLabel(String email, UUID orderId, String size) {

        Account moderator = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (moderator.getWarehouse() == null
                || !order.getWarehouse().getId().equals(moderator.getWarehouse().getId())) {
            throw new ForbiddenException("Bạn không có quyền in phiếu vận đơn của đơn này.");
        }

        if (!StringUtils.hasText(order.getGhnTrackingCode())) {
            throw new ConflictException("Đơn chưa có vận đơn, vui lòng tạo vận đơn trước.", "SHIPMENT_NOT_CREATED");
        }

        String printPath;
        String normalizedSize = size == null ? "A5" : size.trim().toUpperCase();
        switch (normalizedSize) {
            case "A5" -> printPath = "printA5";
            case "80X80" -> printPath = "print80x80";
            case "52X70" -> printPath = "print52x70";
            default -> throw new BadRequestException("Khổ giấy không hợp lệ (A5, 80x80, 52x70).");
        }

        GhnGenTokenResponse tokenResponse;
        try {
            tokenResponse = ghnApi.genPrintToken(ghnToken, ghnShopId,
                    GhnGenTokenRequest.builder().orderCodes(List.of(order.getGhnTrackingCode())).build());
        } catch (Exception ex) {
            log.error(">>>> Loi goi GHN gen-token in phieu cho orderNo={}: {}", order.getOrderNo(), ex.getMessage());
            throw new GhnException("GHN_ERROR: không thể lấy phiếu in, vui lòng thử lại.");
        }

        if (tokenResponse == null || tokenResponse.getData() == null
                || !StringUtils.hasText(tokenResponse.getData().getToken())) {
            log.error(">>>> GHN khong tra ve token in cho orderNo={}: {}", order.getOrderNo(),
                    tokenResponse != null ? tokenResponse.getMessage() : null);
            throw new GhnException("GHN_ERROR: không thể lấy phiếu in, vui lòng thử lại.");
        }

        String host = ghnBaseUrl.replaceAll("/shiip/public-api/?$", "");
        String printUrl = host + "/a5/public-api/" + printPath + "?token=" + tokenResponse.getData().getToken();

        return ShipmentLabelResponse.builder()
                .id(order.getId().toString())
                .ghnTrackingCode(order.getGhnTrackingCode())
                .size(normalizedSize.toLowerCase().equals("a5") ? "A5" : normalizedSize.toLowerCase())
                .printUrl(printUrl)
                .build();
    }

    @Override
    public OrderTrackingResponse getTracking(String email, UUID orderId) {

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền xem đơn này.");
        }

        List<OrderTrackingEvent> trackingEvents =
                orderTrackingEventRepository.findAllByOrder_IdOrderByOccurredAtAsc(orderId);

        List<OrderTrackingEventResponse> events = trackingEvents.stream()
                .map(e -> OrderTrackingEventResponse.builder()
                        .status(e.getGhnStatus())
                        .description(e.getDescription())
                        .location(e.getLocation())
                        .timestamp(e.getOccurredAt())
                        .build())
                .toList();

        OrderTrackingEvent latest = trackingEvents.isEmpty()
                ? null
                : trackingEvents.get(trackingEvents.size() - 1);
        boolean hasIssue = latest != null && isIssueStatus(latest.getGhnStatus());

        return OrderTrackingResponse.builder()
                .id(order.getId().toString())
                .orderNo(order.getOrderNo())
                .status(order.getStatus().name())
                .ghnTrackingCode(order.getGhnTrackingCode())
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .deliveredAt(order.getDeliveredAt())
                .hasIssue(hasIssue)
                .issueNote(hasIssue ? latest.getDescription() : null)
                .events(events)
                .build();
    }

    private static boolean isIssueStatus(String ghnStatus) {
        return ghnStatus != null && ISSUE_GHN_STATUSES.contains(ghnStatus.toLowerCase(Locale.ROOT));
    }

    @Override
    @Transactional
    public void cancelOrderForCarrierFailure(UUID orderId, String reason) {

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null
                || (order.getStatus() != OrderStatus.PACKING && order.getStatus() != OrderStatus.PACKED && order.getStatus() != OrderStatus.SHIPPING)) {
            return;
        }

        releaseReservedProducts(orderId);

        String note = "Hoàn tiền do vận chuyển GHN lỗi - đơn " + order.getOrderNo()
                + (StringUtils.hasText(reason) ? " (" + reason + ")" : "");
        refundBuyerAndCancel(order, CancelReason.CARRIER_FAILED, note);
        auditLogger.logSystemOperation("CANCEL_ORDER_CARRIER_FAILURE", "ORDER", orderId, "CANCELLED", "GHN");
        releaseVoucherIfAllOrdersCancelled(order);

        log.info(">>>> Tu huy don do GHN loi: orderNo={}, reason={}", order.getOrderNo(), reason);
    }

    private GhnCreateOrderRequest buildGhnCreateOrderRequest(Order order, List<OrderItem> items) {
        Warehouse warehouse = order.getWarehouse();

        List<GhnOrderItem> ghnItems = items.stream()
                .map(oi -> GhnOrderItem.builder()
                        .name(oi.getProduct().getName())
                        .code(oi.getProduct().getId())
                        .quantity(1)
                        .price(oi.getPriceAtOrder().intValue())
                        .build())
                .toList();

        return GhnCreateOrderRequest.builder()
                .clientOrderCode(order.getId().toString())
                .toName(order.getShippingRecipientName())
                .toPhone(order.getShippingPhone())
                .toAddress(order.getShippingAddressDetail())
                .toWardName(trimToNull(order.getShippingWardName()))
                .toDistrictName(Boolean.TRUE.equals(order.getShippingIsNewAddress())
                        ? null : trimToNull(order.getShippingDistrictName()))
                .toProvinceName(trimToNull(order.getShippingProvinceName()))
                .isNewToAddress(Boolean.TRUE.equals(order.getShippingIsNewAddress()))
                .fromName(warehouse.getName())
                .fromPhone(warehouse.getPhone())
                .fromAddress(warehouse.getAddress())
                .fromWardName(trimToNull(warehouse.getWardName()))
                .fromDistrictName(Boolean.TRUE.equals(warehouse.getIsNewAddress())
                        ? null : trimToNull(warehouse.getDistrictName()))
                .fromProvinceName(trimToNull(warehouse.getProvinceName()))
                .isNewFromAddress(Boolean.TRUE.equals(warehouse.getIsNewAddress()))
                .weight(defaultPackageWeightGrams)
                .length(defaultPackageLengthCm)
                .width(defaultPackageWidthCm)
                .height(defaultPackageHeightCm)
                .serviceTypeId(2)
                .paymentTypeId(1)
                .requiredNote("CHOXEMHANGKHONGTHU")
                .codAmount(0)
                .orderValue(order.getSubtotal().intValue())
                .items(ghnItems)
                .build();
    }

    private static Instant parseGhnTime(String time) {
        if (!StringUtils.hasText(time)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(time).toInstant();
        } catch (DateTimeParseException ex) {
            log.warn(">>>> Khong doc duoc expected_delivery_time tu GHN: {}", time);
            return null;
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void logGhnAddressBlocks(Order order, GhnCreateOrderRequest ghnRequest) {
        log.error(">>>> Dia chi gui GHN cho orderNo={}: to_name=[{}] to_phone=[{}] to_address=[{}] "
                        + "to_ward_name=[{}] to_district_name=[{}] to_province_name=[{}] is_new_to_address={}",
                order.getOrderNo(), ghnRequest.getToName(), ghnRequest.getToPhone(), ghnRequest.getToAddress(),
                ghnRequest.getToWardName(), ghnRequest.getToDistrictName(), ghnRequest.getToProvinceName(),
                ghnRequest.getIsNewToAddress());
        log.error(">>>> Dia chi kho gui GHN cho orderNo={}: from_address=[{}] from_ward_name=[{}] "
                        + "from_district_name=[{}] from_province_name=[{}] is_new_from_address={}",
                order.getOrderNo(), ghnRequest.getFromAddress(), ghnRequest.getFromWardName(),
                ghnRequest.getFromDistrictName(), ghnRequest.getFromProvinceName(),
                ghnRequest.getIsNewFromAddress());
    }

    private void releaseReservedProducts(UUID orderId) {
        for (OrderItem item : orderItemRepository.findAllByOrder_Id(orderId)) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId()).orElse(null);
            if (product != null && product.getStatus() == ProductStatus.RESERVED) {
                product.setStatus(ProductStatus.ON_SALE);
                productRepository.save(product);
            }
        }
    }

    @Override
    @Transactional
    public void refundOrderForReturn(UUID orderId, String note) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (order.getStatus() == OrderStatus.RETURNED) {
            return;
        }

        refundBuyerWallet(order, note);

        auditLogger.logActorOrSystemOperation("REFUND_RETURN_ORDER", "ORDER", orderId, "REFUNDED", "RETURN_WORKFLOW");
        order.setStatus(OrderStatus.RETURNED);
        order.setRefundAmount(order.getTotalAmount());
        orderRepository.save(order);

        for (OrderItem item : orderItemRepository.findAllByOrder_Id(orderId)) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId()).orElse(null);
            if (product != null
                    && (product.getStatus() == ProductStatus.SOLD || product.getStatus() == ProductStatus.RESERVED)) {
                product.setStatus(ProductStatus.RETURNED);
                productRepository.save(product);
            }
        }
    }

    @Override
    @Transactional
    public void applyReturnedItemAction(UUID orderId, ReturnedItemAction action) {

        ProductStatus targetStatus = switch (action) {
            case RELIST -> ProductStatus.ON_SALE;
            case RETURN_TO_SELLER -> ProductStatus.WITHDRAWN;
            case DISCARD -> ProductStatus.DESTROYED;
        };

        for (OrderItem item : orderItemRepository.findAllByOrder_Id(orderId)) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId()).orElse(null);
            if (product != null && product.getStatus() == ProductStatus.RETURNED) {
                product.setStatus(targetStatus);
                productRepository.save(product);
            }
        }
    }

    private void refundBuyerAndCancel(Order order, CancelReason reason, String note) {
        refundBuyerWallet(order, note);

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        businessNotifications.order(order, "Đơn hàng đã huỷ", note + ". Tiền đã được hoàn vào ví.");
        order.setCancelledAt(Instant.now());
        order.setRefundAmount(order.getTotalAmount());
        orderRepository.save(order);
        queueService.dequeue(QueueType.PACKING, order.getId().toString());
    }

    private void refundBuyerWallet(Order order, String note) {
        Wallet wallet = getOrCreateWallet(order.getBuyer());
        wallet.setBalance(wallet.getBalance().add(order.getTotalAmount()));
        walletRepository.save(wallet);

        WalletTransaction refundTransaction = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.REFUND)
                .amount(order.getTotalAmount())
                .balanceAfter(wallet.getBalance())
                .relatedOrder(order)
                .status(TransactionStatus.SUCCESS)
                .note(note)
                .build();
        walletTransactionRepository.save(refundTransaction);
        recordPlatformCounterpart(refundTransaction);
    }

    private Wallet getOrCreateWallet(Account member) {
        return walletRepository.findByMember_Id(member.getId())
                .orElseGet(() -> Wallet.builder().member(member).build());
    }

    private Product lockAndValidateProduct(String productId, UUID buyerId) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));

        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new ConflictException("Sản phẩm không còn để bán.", "PRODUCT_NOT_AVAILABLE");
        }
        if (product.getMember().getId().equals(buyerId)) {
            throw new ConflictException(
                    "Bạn không thể mua món do chính mình ký gửi.", "CANNOT_BUY_OWN_PRODUCT");
        }
        return product;
    }

    private record VoucherApplication(
            com.rehome.rehome.entity.membership.MemberVoucher voucher,
            String code,
            String name,
            Map<String, BigDecimal> discountByProductId) {

        static VoucherApplication none() {
            return new VoucherApplication(null, null, null, Map.of());
        }

        BigDecimal discountOf(String productId) {
            return discountByProductId.getOrDefault(productId, BigDecimal.ZERO);
        }
    }

    private VoucherApplication applyVoucher(String voucherCode, Account buyer,
                                            List<Product> products, List<BigDecimal> shippingFees) {
        if (!StringUtils.hasText(voucherCode)) {
            return VoucherApplication.none();
        }

        MemberVoucher voucher = memberVoucherRepository.findByCodeForUpdate(voucherCode.trim())
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        List<VoucherCalculator.OrderLine> lines = new ArrayList<>(products.size());
        BigDecimal totalSubtotal = BigDecimal.ZERO;
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            lines.add(new VoucherCalculator.OrderLine(
                    product.getId(),
                    product.getCurrentPrice(),
                    shippingFees.get(i),
                    product.getCurrentPrice(),
                    commissionRateForTerm(product)));
            totalSubtotal = totalSubtotal.add(product.getCurrentPrice());
        }

        voucherCalculator.assertUsable(voucher, buyer.getId(), totalSubtotal, Instant.now());

        VoucherCalculator.Result result = voucherCalculator.calculate(voucher, lines, true);

        Map<String, BigDecimal> byProduct = new LinkedHashMap<>();
        for (VoucherCalculator.Allocation allocation : result.allocations()) {
            byProduct.put(allocation.orderKey(), allocation.discount());
        }

        voucher.setStatus(MemberVoucherStatus.USED);
        voucher.setUsedAt(Instant.now());
        memberVoucherRepository.save(voucher);

        log.info(">>>> Ap voucher {} cho {} don cua buyerId={}: tong giam {}",
                voucher.getCode(), lines.size(), buyer.getId(), result.totalDiscount());

        return new VoucherApplication(voucher, voucher.getCode(),
                voucher.getVoucherTemplate().getName(), byProduct);
    }

    private void releaseVoucherIfAllOrdersCancelled(Order cancelledOrder) {
        MemberVoucher voucher = cancelledOrder.getVoucher();
        if (voucher == null) {
            return;
        }

        cancelledOrder.setVoucher(null);
        cancelledOrder.setDiscountAmount(BigDecimal.ZERO);
        orderRepository.save(cancelledOrder);

        for (Order sibling : orderRepository.findAllByVoucher_Id(voucher.getId())) {
            if (sibling.getStatus() != OrderStatus.CANCELLED) {
                log.info(">>>> Chua tra ma {}: don {} trong cung lo van con trang thai {}",
                        voucher.getCode(), sibling.getOrderNo(), sibling.getStatus());
                return;
            }
        }

        MemberVoucher locked = memberVoucherRepository.findByCodeForUpdate(voucher.getCode())
                .orElse(null);
        if (locked == null || locked.getStatus() != MemberVoucherStatus.USED) {
            return;
        }

        if (!locked.getExpiresAt().isAfter(Instant.now())) {
            locked.setStatus(MemberVoucherStatus.EXPIRED);
        } else {
            locked.setStatus(MemberVoucherStatus.UNUSED);
            locked.setUsedAt(null);
        }
        memberVoucherRepository.save(locked);
        log.info(">>>> Tra ma {} ve trang thai {} sau khi moi don trong lo da huy",
                locked.getCode(), locked.getStatus());
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal commissionRateForTerm(Product product) {
        ConsignmentReceipt receipt = product.getConsignmentReceipt();
        return switch (product.getCurrentTerm()) {
            case 1 -> receipt.getCommissionRateTerm1();
            case 2 -> receipt.getCommissionRateTerm2();
            default -> receipt.getCommissionRateTerm3();
        };
    }

    private String generateOrderNo() {
        String prefix = "OD-" + Year.now(ZoneOffset.UTC).getValue() + "-";
        long seq = orderRepository.countByOrderNoStartingWith(prefix) + 1;
        return prefix + String.format("%06d", seq);
    }

    private String generateTransactionNo() {
        String prefix = "WT-" + Year.now(ZoneOffset.UTC).getValue() + "-";
        long seq = walletTransactionRepository.countByTransactionNoStartingWith(prefix) + 1;
        return prefix + String.format("%06d", seq);
    }

    private PlatformFund getOrCreatePlatformFund() {
        return platformFundRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> PlatformFund.builder().build());
    }

    private void recordPlatformRevenue(WalletTransactionType type, BigDecimal amount, Order order,
                                       OrderItem item, String note) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        PlatformFund fund = getOrCreatePlatformFund();
        if (fund.getId() == null) {
            platformFundRepository.save(fund);
        }

        WalletTransaction revenueTransaction = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.PLATFORM)
                .walletId(fund.getId())
                .type(type)
                .amount(amount)
                .balanceAfter(fund.getBalance())
                .relatedOrder(order)
                .relatedOrderItem(item)
                .status(TransactionStatus.SUCCESS)
                .note(note)
                .build();
        walletTransactionRepository.save(revenueTransaction);
    }

    private void recordPlatformPromotionCost(BigDecimal amount, Order order, String note) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        PlatformFund fund = getOrCreatePlatformFund();
        if (fund.getId() == null) {
            platformFundRepository.save(fund);
        }

        walletTransactionRepository.save(WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.PLATFORM)
                .walletId(fund.getId())
                .type(WalletTransactionType.VOUCHER_DISCOUNT)
                .amount(amount.negate())
                .balanceAfter(fund.getBalance())
                .relatedOrder(order)
                .status(TransactionStatus.SUCCESS)
                .note(note)
                .build());
    }

    private void recordGatewayPaymentReceived(Order order, String note) {
        BigDecimal amount = order.getTotalAmount();
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        PlatformFund fund = getOrCreatePlatformFund();
        if (fund.getId() == null) {
            platformFundRepository.save(fund);
        }
        fund.setBalance(fund.getBalance().add(amount));
        platformFundRepository.save(fund);

        walletTransactionRepository.save(WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.PLATFORM)
                .walletId(fund.getId())
                .type(WalletTransactionType.ORDER_PAYMENT)
                .amount(amount)
                .balanceAfter(fund.getBalance())
                .relatedOrder(order)
                .status(TransactionStatus.SUCCESS)
                .note(note)
                .build());
    }

    private void recordPlatformCounterpart(WalletTransaction memberTransaction) {
        PlatformFund fund = getOrCreatePlatformFund();
        BigDecimal counterpartAmount = memberTransaction.getAmount().negate();
        fund.setBalance(fund.getBalance().add(counterpartAmount));
        platformFundRepository.save(fund);

        WalletTransaction platformTransaction = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.PLATFORM)
                .walletId(fund.getId())
                .type(memberTransaction.getType())
                .amount(counterpartAmount)
                .balanceAfter(fund.getBalance())
                .relatedOrder(memberTransaction.getRelatedOrder())
                .relatedOrderItem(memberTransaction.getRelatedOrderItem())
                .counterpartTransaction(memberTransaction)
                .status(TransactionStatus.SUCCESS)
                .note(memberTransaction.getNote())
                .build();
        walletTransactionRepository.save(platformTransaction);
    }
}
