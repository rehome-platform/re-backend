package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.request.RateOrderRequest;
import com.rehome.rehome.dto.response.OrderCancelResponse;
import com.rehome.rehome.dto.response.OrderConfirmReceiptResponse;
import com.rehome.rehome.dto.response.OrderDetailItemResponse;
import com.rehome.rehome.dto.response.OrderDetailResponse;
import com.rehome.rehome.dto.response.OrderListResponse;
import com.rehome.rehome.dto.response.OrderRatingResponse;
import com.rehome.rehome.dto.response.OrderResponse;
import com.rehome.rehome.dto.response.OrderSummaryResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CancelReason;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.marketplace.Cart;
import com.rehome.rehome.entity.marketplace.CartItem;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Payment;
import com.rehome.rehome.entity.marketplace.PlatformFund;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CartItemRepository;
import com.rehome.rehome.repository.CartRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.PaymentRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import com.rehome.rehome.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final AccountRepository accountRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final PaymentRepository paymentRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PlatformFundRepository platformFundRepository;

    // Phí ship cố định tạm thời, chưa tích hợp GHN
    @Value("${app.order.shipping-fee}")
    private BigDecimal shippingFee;

    @Value("${app.order.payment-deadline-minutes}")
    private long paymentDeadlineMinutes;

    // Chưa có cấu hình thuế thật, tạm để 0
    @Value("${app.order.tax-rate:0}")
    private BigDecimal taxRate;

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
                lockedProducts.add(lockAndValidateProduct(productId));
            }

            // Món vừa đặt mà cũng đang nằm trong giỏ thì dọn khỏi giỏ, để các món còn lại
            // (ví dụ đã hết hàng) không bị kẹt theo.
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
                lockedProducts.add(lockAndValidateProduct(productId));
            }
        }

        // Giỏ nhiều kho bị tách thành nhiều đơn, mỗi đơn đúng 1 kho (F-053)
        Map<Warehouse, List<Product>> productsByWarehouse = lockedProducts.stream()
                .collect(Collectors.groupingBy(Product::getWarehouse, LinkedHashMap::new, Collectors.toList()));

        Instant paymentDeadline = Instant.now().plus(paymentDeadlineMinutes, ChronoUnit.MINUTES);
        List<OrderResponse> responses = new ArrayList<>();

        for (Map.Entry<Warehouse, List<Product>> entry : productsByWarehouse.entrySet()) {
            Warehouse warehouse = entry.getKey();
            List<Product> products = entry.getValue();

            BigDecimal subtotal = products.stream()
                    .map(Product::getCurrentPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Order order = Order.builder()
                    .orderNo(generateOrderNo())
                    .buyer(buyer)
                    .warehouse(warehouse)
                    .shippingAddress(shippingAddress)
                    .shippingRecipientName(shippingAddress.getRecipientName())
                    .shippingPhone(shippingAddress.getPhone())
                    .shippingAddressDetail(shippingAddress.getAddressDetail())
                    .subtotal(subtotal)
                    .shippingFee(shippingFee)
                    .totalAmount(subtotal.add(shippingFee))
                    .paymentMethod(OrderPaymentMethod.WALLET)
                    .status(OrderStatus.PENDING_PAYMENT)
                    .paymentDeadline(paymentDeadline)
                    .build();
            orderRepository.save(order);

            for (Product product : products) {
                OrderItem item = OrderItem.builder()
                        .order(order)
                        .product(product)
                        .priceAtOrder(product.getCurrentPrice())
                        .commissionRateApplied(commissionRateForTerm(product))
                        .taxRateApplied(taxRate)
                        .termApplied(product.getCurrentTerm())
                        .build();
                orderItemRepository.save(item);

                // F-055: RESERVED ngay lúc đặt hàng, không phải từ lúc thêm giỏ
                product.setStatus(ProductStatus.RESERVED);
                productRepository.save(product);
            }

            responses.add(OrderResponse.builder()
                    .id(order.getId().toString())
                    .orderNo(order.getOrderNo())
                    .warehouseId(warehouse.getId().toString())
                    .shippingRecipientName(order.getShippingRecipientName())
                    .shippingPhone(order.getShippingPhone())
                    .shippingAddressDetail(order.getShippingAddressDetail())
                    .subtotal(order.getSubtotal())
                    .shippingFee(order.getShippingFee())
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

        Page<OrderSummaryResponse> mapped = result.map(order -> {
            List<OrderItem> items = itemsByOrderId.getOrDefault(order.getId(), List.of());
            OrderItem firstItem = items.isEmpty() ? null : items.get(0);

            return OrderSummaryResponse.builder()
                    .id(order.getId().toString())
                    .orderNo(order.getOrderNo())
                    .status(order.getStatus().name())
                    .totalAmount(order.getTotalAmount())
                    .warehouseName(order.getWarehouse().getName())
                    .itemCount(items.size())
                    .firstItemName(firstItem != null ? firstItem.getProduct().getName() : null)
                    .firstItemThumbnail(firstItem != null
                            ? thumbnailByProductId.get(firstItem.getProduct().getId()) : null)
                    .paymentDeadline(order.getPaymentDeadline())
                    .deliveredAt(order.getDeliveredAt())
                    .autoCompleteDeadline(order.getAutoCompleteDeadline())
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
    public OrderDetailResponse getOrderDetail(String email, UUID orderId) {

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền xem đơn này.");
        }

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
                            .build();
                })
                .toList();

        Instant paidAt = paymentRepository.findByOrder_Id(orderId)
                .map(Payment::getPaidAt)
                .orElse(null);

        return OrderDetailResponse.builder()
                .id(order.getId().toString())
                .orderNo(order.getOrderNo())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .paymentMethod(order.getPaymentMethod().name())
                .paidAt(paidAt)
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .warehouseName(order.getWarehouse().getName())
                .shippingRecipientName(order.getShippingRecipientName())
                .shippingPhone(order.getShippingPhone())
                .shippingAddressDetail(order.getShippingAddressDetail())
                .items(items)
                .ghnTrackingCode(order.getGhnTrackingCode())
                .deliveredAt(order.getDeliveredAt())
                .autoCompleteDeadline(order.getAutoCompleteDeadline())
                .serviceRating(order.getServiceRating())
                .ratedAt(order.getRatedAt())
                .cancelReason(order.getCancelReason() != null ? order.getCancelReason().name() : null)
                .cancelledAt(order.getCancelledAt())
                .refundAmount(order.getRefundAmount())
                .completedAt(order.getCompletedAt())
                .ratingComment(order.getRatingComment())
                .build();
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

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("Đơn chưa thanh toán, không thể hủy theo cách này.", "ORDER_NOT_PAID_YET");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ConflictException("Đơn đã hủy trước đó.", "ORDER_ALREADY_CANCELLED");
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException("Đơn đã đóng gói, không thể hủy.", "CANNOT_CANCEL_AFTER_PACKING");
        }

        for (OrderItem item : orderItemRepository.findAllByOrder_Id(orderId)) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId()).orElse(null);
            if (product != null && product.getStatus() == ProductStatus.RESERVED) {
                product.setStatus(ProductStatus.ON_SALE);
                productRepository.save(product);
            }
        }

        Wallet wallet = getOrCreateWallet(buyer);
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
                .note("Hoàn tiền hủy đơn " + order.getOrderNo())
                .build();
        walletTransactionRepository.save(refundTransaction);
        recordPlatformCounterpart(refundTransaction);

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(CancelReason.BUYER_CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setRefundAmount(order.getTotalAmount());
        orderRepository.save(order);

        return OrderCancelResponse.builder()
                .id(order.getId().toString())
                .status(order.getStatus().name())
                .build();
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

        for (OrderItem item : orderItemRepository.findAllByOrder_Id(orderId)) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId()).orElse(null);
            if (product != null && product.getStatus() == ProductStatus.RESERVED) {
                product.setStatus(ProductStatus.SOLD);
                productRepository.save(product);
            }

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
                    .note("Thanh toán đơn hàng " + order.getOrderNo())
                    .build();
            walletTransactionRepository.save(payoutTransaction);
            recordPlatformCounterpart(payoutTransaction);
        }

        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        orderRepository.save(order);

        return OrderConfirmReceiptResponse.builder()
                .id(order.getId().toString())
                .status(order.getStatus().name())
                .build();
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

        order.setServiceRating(request.getServiceRating());
        order.setRatingComment(request.getComment());
        order.setRatedAt(Instant.now());
        orderRepository.save(order);

        return OrderRatingResponse.builder()
                .id(order.getId().toString())
                .serviceRating(order.getServiceRating())
                .comment(order.getRatingComment())
                .build();
    }

    private Wallet getOrCreateWallet(Account member) {
        return walletRepository.findByMember_Id(member.getId())
                .orElseGet(() -> Wallet.builder().member(member).build());
    }

    private Product lockAndValidateProduct(String productId) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));

        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new ConflictException("Sản phẩm không còn để bán.", "PRODUCT_NOT_AVAILABLE");
        }
        return product;
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

    // F-039: mỗi bút toán ví Member phải có một bút toán đối ứng ở quỹ nền tảng, ngược dấu, để sổ sách luôn cân
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
