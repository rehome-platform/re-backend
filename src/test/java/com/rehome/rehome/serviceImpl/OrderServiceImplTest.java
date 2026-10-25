package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnShippingFeeCalculator;
import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.request.PayOrdersRequest;
import com.rehome.rehome.dto.request.RateOrderRequest;
import com.rehome.rehome.dto.response.OrderCancelResponse;
import com.rehome.rehome.dto.response.OrderConfirmReceiptResponse;
import com.rehome.rehome.dto.response.OrderDetailResponse;
import com.rehome.rehome.dto.response.OrderListResponse;
import com.rehome.rehome.dto.response.OrderRatingResponse;
import com.rehome.rehome.dto.response.OrderResponse;
import com.rehome.rehome.dto.response.OrderSummaryResponse;
import com.rehome.rehome.dto.response.OrderTrackingResponse;
import com.rehome.rehome.dto.response.PayOrdersResponse;
import com.rehome.rehome.dto.response.SoldOrderListResponse;
import com.rehome.rehome.dto.response.SoldOrderResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CancelReason;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.marketplace.Brand;
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
import com.rehome.rehome.entity.marketplace.ReturnRequest;
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
import com.rehome.rehome.repository.OrderTrackingEventRepository;
import com.rehome.rehome.repository.PaymentRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock AccountRepository accountRepository;
    @Mock ShippingAddressRepository shippingAddressRepository;
    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock OrderTrackingEventRepository orderTrackingEventRepository;
    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock ProductPhotoRepository productPhotoRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock WalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock PlatformFundRepository platformFundRepository;
    @Mock GhnShippingFeeCalculator ghnShippingFeeCalculator;

    @InjectMocks
    OrderServiceImpl orderService;

    Account member;
    Account seller;
    String email;
    ShippingAddress address;
    Warehouse warehouse1;
    Warehouse warehouse2;
    ConsignmentReceipt receipt;
    Product product1;
    Product product2;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(orderService, "paymentDeadlineMinutes", 15L);
        ReflectionTestUtils.setField(orderService, "taxRate", BigDecimal.ZERO);
        ReflectionTestUtils.setField(orderService, "returnRequestWindowDays", 3L);

        email = "member@example.com";
        member = Account.builder().id(UUID.randomUUID()).email(email).build();

        address = ShippingAddress.builder().id(UUID.randomUUID()).member(member)
                .recipientName("Tạ Thị Ngọc Anh")
                .phone("0912345678")
                .addressDetail("12 Nguyễn Trãi, Q.1, TP.HCM")
                .build();

        warehouse1 = Warehouse.builder().id(UUID.randomUUID()).name("Kho Quận 7").build();
        warehouse2 = Warehouse.builder().id(UUID.randomUUID()).name("Kho Bình Thạnh").build();

        receipt = ConsignmentReceipt.builder()
                .commissionRateTerm1(new BigDecimal("0.10"))
                .commissionRateTerm2(new BigDecimal("0.15"))
                .commissionRateTerm3(new BigDecimal("0.20"))
                .build();

        // Product.member_id là NOT NULL, và createOrders phải so người ký gửi với người mua
        seller = Account.builder().id(UUID.randomUUID()).email("seller@example.com").build();

        product1 = Product.builder()
                .id("Q7-AK-000845")
                .status(ProductStatus.ON_SALE)
                .currentPrice(new BigDecimal("220000"))
                .currentTerm((short) 1)
                .warehouse(warehouse1)
                .consignmentReceipt(receipt)
                .member(seller)
                .build();

        product2 = Product.builder()
                .id("Q7-AK-000912")
                .status(ProductStatus.ON_SALE)
                .currentPrice(new BigDecimal("380000"))
                .currentTerm((short) 1)
                .warehouse(warehouse2)
                .consignmentReceipt(receipt)
                .member(seller)
                .build();
    }

    private void stubOrderSaveAssignsId() {
        when(orderRepository.save(any())).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void createOrders_throwsBadRequest_whenShippingAddressIdIsBlank() {
        CreateOrderRequest request = new CreateOrderRequest("  ", List.of("Q7-AK-000845"));

        assertThatThrownBy(() -> orderService.createOrders(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("shippingAddressId là bắt buộc.");

        verify(accountRepository, never()).findByEmail(any());
    }

    @Test
    void createOrders_throwsBadRequest_whenShippingAddressIdIsNotValidUuid() {
        CreateOrderRequest request = new CreateOrderRequest("addr-001", List.of("Q7-AK-000845"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> orderService.createOrders(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("shippingAddressId không hợp lệ.");

        verify(shippingAddressRepository, never()).findById(any());
    }

    @Test
    void createOrders_throwsNotFound_whenAccountDoesNotExist() {
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), List.of("Q7-AK-000845"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrders(email, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");
    }

    @Test
    void createOrders_throwsNotFound_whenShippingAddressDoesNotBelongToMember() {
        Account anotherMember = Account.builder().id(UUID.randomUUID()).email("other@example.com").build();
        ShippingAddress othersAddress = ShippingAddress.builder().id(address.getId()).member(anotherMember).build();
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), List.of("Q7-AK-000845"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(othersAddress));

        assertThatThrownBy(() -> orderService.createOrders(email, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy địa chỉ.");

        verify(productRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void createOrders_throwsBadRequest_whenNoProductIdsGiven_andMemberHasNoCart() {
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), List.of());

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(cartRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrders(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Giỏ hàng trống.");
    }

    @Test
    void createOrders_throwsBadRequest_whenCartHasNoItems() {
        Cart cart = Cart.builder().id(UUID.randomUUID()).member(member).build();
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), null);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(cartRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdWithProduct(cart.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrders(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Giỏ hàng trống.");
    }

    @Test
    void createOrders_throwsNotFound_whenBuyNowProductDoesNotExist() {
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), List.of("P-404"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(productRepository.findByIdForUpdate("P-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrders(email, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy sản phẩm.");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrders_throwsConflict_whenBuyNowProductIsNotOnSale() {
        product1.setStatus(ProductStatus.RESERVED);
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), List.of("Q7-AK-000845"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(productRepository.findByIdForUpdate("Q7-AK-000845")).thenReturn(Optional.of(product1));

        assertThatThrownBy(() -> orderService.createOrders(email, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Sản phẩm không còn để bán.");

        verify(orderRepository, never()).save(any());
    }

    // Buy-now không đi qua giỏ nên phải chặn lại ở createOrders, không chỉ ở addItem
    @Test
    void createOrders_throwsConflict_whenBuyNowProductIsOwnedByBuyer() {
        product1.setMember(member);
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), List.of("Q7-AK-000845"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(productRepository.findByIdForUpdate("Q7-AK-000845")).thenReturn(Optional.of(product1));

        assertThatThrownBy(() -> orderService.createOrders(email, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Bạn không thể mua món do chính mình ký gửi.");

        verify(orderRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void createOrders_createsSingleOrder_forBuyNowFlow_andReservesProduct() {
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), List.of("Q7-AK-000845"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(productRepository.findByIdForUpdate("Q7-AK-000845")).thenReturn(Optional.of(product1));
        when(ghnShippingFeeCalculator.calculate(any(), any(), any())).thenReturn(new BigDecimal("25000"));
        stubOrderSaveAssignsId();

        List<OrderResponse> responses = orderService.createOrders(email, request);

        assertThat(responses).hasSize(1);
        OrderResponse response = responses.get(0);
        assertThat(response.getId()).isNotNull();
        assertThat(response.getWarehouseId()).isEqualTo(warehouse1.getId().toString());
        assertThat(response.getShippingRecipientName()).isEqualTo("Tạ Thị Ngọc Anh");
        assertThat(response.getShippingPhone()).isEqualTo("0912345678");
        assertThat(response.getShippingAddressDetail()).isEqualTo("12 Nguyễn Trãi, Q.1, TP.HCM");
        assertThat(response.getSubtotal()).isEqualTo(new BigDecimal("220000"));
        assertThat(response.getShippingFee()).isEqualTo(new BigDecimal("25000"));
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("245000"));
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
        assertThat(response.getOrderNo()).startsWith("OD-" + Year.now(ZoneOffset.UTC).getValue() + "-");
        assertThat(response.getPaymentDeadline()).isAfter(java.time.Instant.now());

        assertThat(product1.getStatus()).isEqualTo(ProductStatus.RESERVED);
        verify(productRepository).save(product1);

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getProduct()).isEqualTo(product1);
        assertThat(itemCaptor.getValue().getPriceAtOrder()).isEqualTo(new BigDecimal("220000"));
        assertThat(itemCaptor.getValue().getCommissionRateApplied()).isEqualTo(new BigDecimal("0.10"));

        verify(cartItemRepository, never()).deleteByCart_IdAndProduct_IdIn(any(), any());
    }

    @Test
    void createOrders_buyNow_removesOrderedProductFromCart_whenAlsoPresentThere() {
        Cart cart = Cart.builder().id(UUID.randomUUID()).member(member).build();
        CartItem item1 = CartItem.builder().id(UUID.randomUUID()).cart(cart).product(product1).build();
        CartItem item2 = CartItem.builder().id(UUID.randomUUID()).cart(cart).product(product2).build();
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), List.of("Q7-AK-000845"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(productRepository.findByIdForUpdate("Q7-AK-000845")).thenReturn(Optional.of(product1));
        when(cartRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdWithProduct(cart.getId())).thenReturn(List.of(item1, item2));
        when(ghnShippingFeeCalculator.calculate(any(), any(), any())).thenReturn(new BigDecimal("25000"));
        stubOrderSaveAssignsId();

        orderService.createOrders(email, request);

        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(cartItemRepository).deleteByCart_IdAndProduct_IdIn(eq(cart.getId()), idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly("Q7-AK-000845");
    }

    @Test
    void createOrders_buyNow_doesNotTouchCart_whenOrderedProductNotInCart() {
        Cart cart = Cart.builder().id(UUID.randomUUID()).member(member).build();
        CartItem item2 = CartItem.builder().id(UUID.randomUUID()).cart(cart).product(product2).build();
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), List.of("Q7-AK-000845"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(productRepository.findByIdForUpdate("Q7-AK-000845")).thenReturn(Optional.of(product1));
        when(cartRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdWithProduct(cart.getId())).thenReturn(List.of(item2));
        when(ghnShippingFeeCalculator.calculate(any(), any(), any())).thenReturn(new BigDecimal("25000"));
        stubOrderSaveAssignsId();

        orderService.createOrders(email, request);

        verify(cartItemRepository, never()).deleteByCart_IdAndProduct_IdIn(any(), any());
    }

    @Test
    void createOrders_usesCommissionRateOfCurrentTerm() {
        product1.setCurrentTerm((short) 2);
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), List.of("Q7-AK-000845"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(productRepository.findByIdForUpdate("Q7-AK-000845")).thenReturn(Optional.of(product1));
        when(ghnShippingFeeCalculator.calculate(any(), any(), any())).thenReturn(new BigDecimal("25000"));
        stubOrderSaveAssignsId();

        orderService.createOrders(email, request);

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getCommissionRateApplied()).isEqualTo(new BigDecimal("0.15"));
        assertThat(itemCaptor.getValue().getTermApplied()).isEqualTo((short) 2);
    }

    @Test
    void createOrders_splitsCartIntoOneOrderPerWarehouse_andClearsOrderedCartItems() {
        Cart cart = Cart.builder().id(UUID.randomUUID()).member(member).build();
        CartItem item1 = CartItem.builder().id(UUID.randomUUID()).cart(cart).product(product1).build();
        CartItem item2 = CartItem.builder().id(UUID.randomUUID()).cart(cart).product(product2).build();
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), null);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(cartRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdWithProduct(cart.getId())).thenReturn(List.of(item1, item2));
        when(productRepository.findByIdForUpdate("Q7-AK-000845")).thenReturn(Optional.of(product1));
        when(productRepository.findByIdForUpdate("Q7-AK-000912")).thenReturn(Optional.of(product2));
        when(ghnShippingFeeCalculator.calculate(any(), any(), any())).thenReturn(new BigDecimal("25000"));
        stubOrderSaveAssignsId();

        List<OrderResponse> responses = orderService.createOrders(email, request);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(OrderResponse::getWarehouseId)
                .containsExactlyInAnyOrder(warehouse1.getId().toString(), warehouse2.getId().toString());

        assertThat(product1.getStatus()).isEqualTo(ProductStatus.RESERVED);
        assertThat(product2.getStatus()).isEqualTo(ProductStatus.RESERVED);

        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(cartItemRepository).deleteByCart_IdAndProduct_IdIn(eq(cart.getId()), idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder("Q7-AK-000845", "Q7-AK-000912");
    }

    @Test
    void createOrders_throwsConflict_whenCartProductWasBoughtByAnotherMemberBeforeCheckout() {
        Cart cart = Cart.builder().id(UUID.randomUUID()).member(member).build();
        product1.setStatus(ProductStatus.SOLD);
        CartItem item1 = CartItem.builder().id(UUID.randomUUID()).cart(cart).product(product1).build();
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), null);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(cartRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdWithProduct(cart.getId())).thenReturn(List.of(item1));
        when(productRepository.findByIdForUpdate("Q7-AK-000845")).thenReturn(Optional.of(product1));

        assertThatThrownBy(() -> orderService.createOrders(email, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Sản phẩm không còn để bán.");

        verify(orderRepository, never()).save(any());
        verify(cartItemRepository, never()).deleteByCart_IdAndProduct_IdIn(any(), any());
    }

    private ProductPhoto photo(Product product, String url) {
        return ProductPhoto.builder().id(UUID.randomUUID()).product(product).imageUrl(url).build();
    }

    @Test
    void getOrders_throwsNotFound_whenAccountDoesNotExist() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrders(email, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");
    }

    @Test
    void getOrders_throwsBadRequest_whenStatusValueIsInvalid() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> orderService.getOrders(email, "PAID,NOT_A_STATUS", 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("status không hợp lệ: NOT_A_STATUS");

        verify(orderRepository, never()).findByBuyer_IdAndStatusIn(any(), any(), any());
    }

    @Test
    void getOrders_parsesCommaSeparatedStatuses_caseInsensitively() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findByBuyer_IdAndStatusIn(eq(member.getId()), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(orderRepository.countGroupedByStatusForBuyer(member.getId())).thenReturn(List.of());

        orderService.getOrders(email, "paid, packing", 0, 20);

        ArgumentCaptor<List<OrderStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).findByBuyer_IdAndStatusIn(
                eq(member.getId()), statusesCaptor.capture(), any(Pageable.class));
        assertThat(statusesCaptor.getValue()).containsExactly(OrderStatus.PAID, OrderStatus.PACKING);
    }

    @Test
    void getOrders_defaultsToAllStatuses_whenStatusIsBlank() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findByBuyer_IdAndStatusIn(eq(member.getId()), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(orderRepository.countGroupedByStatusForBuyer(member.getId())).thenReturn(List.of());

        orderService.getOrders(email, "  ", 0, 20);

        ArgumentCaptor<List<OrderStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).findByBuyer_IdAndStatusIn(
                eq(member.getId()), statusesCaptor.capture(), any(Pageable.class));
        assertThat(statusesCaptor.getValue()).containsExactlyInAnyOrder(OrderStatus.values());
    }

    @Test
    void getOrders_mapsSummaryFields_withFirstItemThumbnailAndFullStatusCounts() {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        product1.setName("Áo A");
        product2.setName("Áo B");
        Instant paymentDeadline = Instant.parse("2026-01-10T00:00:00Z");
        Instant deliveredAt = Instant.parse("2026-01-12T00:00:00Z");
        Instant autoCompleteDeadline = Instant.parse("2026-01-15T00:00:00Z");

        Order order1 = Order.builder()
                .id(orderId1)
                .orderNo("OD-2026-000001")
                .buyer(member)
                .warehouse(warehouse1)
                .status(OrderStatus.SHIPPING)
                .totalAmount(new BigDecimal("245000"))
                .paymentDeadline(paymentDeadline)
                .deliveredAt(deliveredAt)
                .autoCompleteDeadline(autoCompleteDeadline)
                .serviceRating((short) 5)
                .build();

        Order order2 = Order.builder()
                .id(orderId2)
                .orderNo("OD-2026-000002")
                .buyer(member)
                .warehouse(warehouse2)
                .status(OrderStatus.PACKING)
                .totalAmount(new BigDecimal("380000"))
                .paymentDeadline(paymentDeadline)
                .build();

        OrderItem item1a = OrderItem.builder().id(UUID.randomUUID()).order(order1).product(product1).build();
        OrderItem item1b = OrderItem.builder().id(UUID.randomUUID()).order(order1).product(product2).build();
        OrderItem item2a = OrderItem.builder().id(UUID.randomUUID()).order(order2).product(product2).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findByBuyer_IdAndStatusIn(eq(member.getId()), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order1, order2)));
        when(orderItemRepository.findAllByOrder_IdInOrderByIdAsc(List.of(orderId1, orderId2)))
                .thenReturn(List.of(item1a, item1b, item2a));
        when(productPhotoRepository.findByProduct_IdInOrderByIdAsc(any()))
                .thenReturn(List.of(photo(product1, "ao-a.jpg"), photo(product2, "ao-b.jpg")));
        when(orderRepository.countGroupedByStatusForBuyer(member.getId()))
                .thenReturn(List.of(
                        new Object[]{OrderStatus.SHIPPING, 1L},
                        new Object[]{OrderStatus.PACKING, 1L}));

        OrderListResponse result = orderService.getOrders(email, null, 0, 20);

        assertThat(result.getOrders().getContent()).hasSize(2);

        OrderSummaryResponse summary1 = result.getOrders().getContent().get(0);
        assertThat(summary1.getId()).isEqualTo(orderId1.toString());
        assertThat(summary1.getOrderNo()).isEqualTo("OD-2026-000001");
        assertThat(summary1.getStatus()).isEqualTo("SHIPPING");
        assertThat(summary1.getTotalAmount()).isEqualTo(new BigDecimal("245000"));
        assertThat(summary1.getWarehouseName()).isEqualTo("Kho Quận 7");
        assertThat(summary1.getItemCount()).isEqualTo(2);
        assertThat(summary1.getFirstItemName()).isEqualTo("Áo A");
        assertThat(summary1.getFirstItemThumbnail()).isEqualTo("ao-a.jpg");
        assertThat(summary1.getPaymentDeadline()).isEqualTo(paymentDeadline);
        assertThat(summary1.getDeliveredAt()).isEqualTo(deliveredAt);
        assertThat(summary1.getAutoCompleteDeadline()).isEqualTo(autoCompleteDeadline);
        assertThat(summary1.getServiceRating()).isEqualTo((short) 5);

        OrderSummaryResponse summary2 = result.getOrders().getContent().get(1);
        assertThat(summary2.getId()).isEqualTo(orderId2.toString());
        assertThat(summary2.getItemCount()).isEqualTo(1);
        assertThat(summary2.getFirstItemName()).isEqualTo("Áo B");
        assertThat(summary2.getFirstItemThumbnail()).isEqualTo("ao-b.jpg");
        assertThat(summary2.getDeliveredAt()).isNull();
        assertThat(summary2.getServiceRating()).isNull();

        assertThat(result.getStatusCounts())
                .containsEntry("SHIPPING", 1L)
                .containsEntry("PACKING", 1L)
                .containsEntry("PENDING_PAYMENT", 0L)
                .containsEntry("PAID", 0L)
                .containsEntry("DELIVERED", 0L)
                .containsEntry("COMPLETED", 0L)
                .containsEntry("CANCELLED", 0L);
    }

    @Test
    void getOrderDetail_throwsNotFound_whenAccountDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderDetail(email, orderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");

        verify(orderRepository, never()).findById(any());
    }

    @Test
    void getOrderDetail_throwsNotFound_whenOrderDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderDetail(email, orderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy đơn hàng.");
    }

    @Test
    void getOrderDetail_throwsForbidden_whenOrderBelongsToAnotherBuyer() {
        UUID orderId = UUID.randomUUID();
        Account anotherMember = Account.builder().id(UUID.randomUUID()).email("other@example.com").build();
        Order order = Order.builder().id(orderId).buyer(anotherMember).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderDetail(email, orderId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không có quyền xem đơn này.");

        verify(orderItemRepository, never()).findAllByOrder_IdWithProductOrderByIdAsc(any());
    }

    @Test
    void getOrderDetail_mapsAllFields_withItemsAndPaidAt() {
        UUID orderId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant paymentDeadline = Instant.parse("2026-01-01T00:15:00Z");
        Instant deliveredAt = Instant.parse("2026-01-05T00:00:00Z");
        Instant autoCompleteDeadline = Instant.parse("2026-01-08T00:00:00Z");
        Instant ratedAt = Instant.parse("2026-01-09T00:00:00Z");
        Instant paidAt = Instant.parse("2026-01-01T00:05:00Z");

        product1.setName("Áo A");
        product1.setBrand(Brand.builder().id(UUID.randomUUID()).name("Nike").build());
        product2.setName("Áo B");
        product2.setBrand(Brand.builder().id(UUID.randomUUID()).name("Adidas").build());

        Order order = Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000001")
                .buyer(member)
                .warehouse(warehouse1)
                .status(OrderStatus.DELIVERED)
                .createdAt(createdAt)
                .paymentMethod(com.rehome.rehome.entity.enums.OrderPaymentMethod.MOMO)
                .subtotal(new BigDecimal("600000"))
                .shippingFee(new BigDecimal("25000"))
                .totalAmount(new BigDecimal("625000"))
                .paymentDeadline(paymentDeadline)
                .shippingRecipientName("Tạ Thị Ngọc Anh")
                .shippingPhone("0912345678")
                .shippingAddressDetail("12 Nguyễn Trãi, Q.1, TP.HCM")
                .ghnTrackingCode("GHN123456789")
                .deliveredAt(deliveredAt)
                .autoCompleteDeadline(autoCompleteDeadline)
                .serviceRating((short) 5)
                .ratedAt(ratedAt)
                .build();

        OrderItem item1 = OrderItem.builder().id(UUID.randomUUID()).order(order)
                .product(product1).priceAtOrder(new BigDecimal("220000")).build();
        OrderItem item2 = OrderItem.builder().id(UUID.randomUUID()).order(order)
                .product(product2).priceAtOrder(new BigDecimal("380000")).build();

        Payment payment = Payment.builder().id(UUID.randomUUID()).order(order)
                .amount(new BigDecimal("625000")).paidAt(paidAt).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(orderId))
                .thenReturn(List.of(item1, item2));
        when(productPhotoRepository.findByProduct_IdInOrderByIdAsc(any()))
                .thenReturn(List.of(photo(product1, "ao-a.jpg"), photo(product2, "ao-b.jpg")));
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));

        OrderDetailResponse result = orderService.getOrderDetail(email, orderId);

        assertThat(result.getId()).isEqualTo(orderId.toString());
        assertThat(result.getOrderNo()).isEqualTo("OD-2026-000001");
        assertThat(result.getStatus()).isEqualTo("DELIVERED");
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        assertThat(result.getPaymentMethod()).isEqualTo("MOMO");
        assertThat(result.getPaidAt()).isEqualTo(paidAt);
        assertThat(result.getSubtotal()).isEqualTo(new BigDecimal("600000"));
        assertThat(result.getShippingFee()).isEqualTo(new BigDecimal("25000"));
        assertThat(result.getTotalAmount()).isEqualTo(new BigDecimal("625000"));
        assertThat(result.getWarehouseName()).isEqualTo("Kho Quận 7");
        assertThat(result.getShippingRecipientName()).isEqualTo("Tạ Thị Ngọc Anh");
        assertThat(result.getShippingPhone()).isEqualTo("0912345678");
        assertThat(result.getShippingAddressDetail()).isEqualTo("12 Nguyễn Trãi, Q.1, TP.HCM");
        assertThat(result.getGhnTrackingCode()).isEqualTo("GHN123456789");
        assertThat(result.getDeliveredAt()).isEqualTo(deliveredAt);
        assertThat(result.getAutoCompleteDeadline()).isEqualTo(autoCompleteDeadline);
        assertThat(result.getServiceRating()).isEqualTo((short) 5);
        assertThat(result.getRatedAt()).isEqualTo(ratedAt);
        assertThat(result.getCancelReason()).isNull();
        assertThat(result.getCancelledAt()).isNull();
        assertThat(result.getRefundAmount()).isNull();

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getProductId()).isEqualTo(product1.getId());
        assertThat(result.getItems().get(0).getName()).isEqualTo("Áo A");
        assertThat(result.getItems().get(0).getThumbnailUrl()).isEqualTo("ao-a.jpg");
        assertThat(result.getItems().get(0).getBrandName()).isEqualTo("Nike");
        assertThat(result.getItems().get(0).getPriceAtOrder()).isEqualTo(new BigDecimal("220000"));
        assertThat(result.getItems().get(1).getBrandName()).isEqualTo("Adidas");
    }

    @Test
    void getOrderDetail_paidAtIsNull_whenNoPaymentRecordExists() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .buyer(member)
                .warehouse(warehouse1)
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentMethod(com.rehome.rehome.entity.enums.OrderPaymentMethod.WALLET)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(orderId)).thenReturn(List.of());
        when(productPhotoRepository.findByProduct_IdInOrderByIdAsc(any())).thenReturn(List.of());
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.empty());

        OrderDetailResponse result = orderService.getOrderDetail(email, orderId);

        assertThat(result.getPaidAt()).isNull();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getOrderDetail_mapsCancelReasonAndCancelledAt_whenOrderWasAutoCancelled() {
        UUID orderId = UUID.randomUUID();
        Instant cancelledAt = Instant.parse("2026-01-01T00:20:00Z");
        Order order = Order.builder()
                .id(orderId)
                .buyer(member)
                .warehouse(warehouse1)
                .status(OrderStatus.CANCELLED)
                .paymentMethod(com.rehome.rehome.entity.enums.OrderPaymentMethod.WALLET)
                .cancelReason(CancelReason.PAYMENT_EXPIRED)
                .cancelledAt(cancelledAt)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(orderId)).thenReturn(List.of());
        when(productPhotoRepository.findByProduct_IdInOrderByIdAsc(any())).thenReturn(List.of());
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.empty());

        OrderDetailResponse result = orderService.getOrderDetail(email, orderId);

        assertThat(result.getCancelReason()).isEqualTo("PAYMENT_EXPIRED");
        assertThat(result.getCancelledAt()).isEqualTo(cancelledAt);
    }

    @Test
    void cancelOrder_throwsNotFound_whenAccountDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(email, orderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");

        verify(orderRepository, never()).findById(any());
    }

    @Test
    void cancelOrder_throwsNotFound_whenOrderDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(email, orderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy đơn hàng.");
    }

    @Test
    void cancelOrder_throwsForbidden_whenOrderBelongsToAnotherBuyer() {
        UUID orderId = UUID.randomUUID();
        Account anotherMember = Account.builder().id(UUID.randomUUID()).email("other@example.com").build();
        Order order = Order.builder().id(orderId).buyer(anotherMember).status(OrderStatus.PAID).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(email, orderId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không có quyền hủy đơn này.");

        verify(orderItemRepository, never()).findAllByOrder_Id(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void cancelOrder_throwsConflictWithCode_whenOrderAlreadyPacked() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).buyer(member).status(OrderStatus.PACKING).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(email, orderId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Đơn đã đóng gói, không thể hủy.")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode())
                        .isEqualTo("CANNOT_CANCEL_AFTER_PACKING"));

        verify(walletRepository, never()).save(any());
    }

    // P3.7: nút "Huỷ đơn" ở màn chờ thanh toán — đơn chưa trả tiền vẫn phải hủy được
    @Test
    void cancelOrder_cancelsWithoutRefund_whenOrderStillPendingPayment() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000001")
                .buyer(member)
                .totalAmount(new BigDecimal("150000"))
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of());

        OrderCancelResponse response = orderService.cancelOrder(email, orderId);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(order.getCancelReason()).isEqualTo(CancelReason.BUYER_CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();

        // Chưa thu tiền thì không được hoàn: cộng khống vào ví là tạo tiền từ không khí
        assertThat(response.getRefundAmount()).isNull();
        assertThat(order.getRefundAmount()).isNull();
        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
    }

    @Test
    void cancelOrder_throwsConflictWithCode_whenOrderAlreadyCancelled() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).buyer(member).status(OrderStatus.CANCELLED).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(email, orderId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Đơn đã hủy trước đó.")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode())
                        .isEqualTo("ORDER_ALREADY_CANCELLED"));

        verify(walletRepository, never()).save(any());
    }

    @Test
    void cancelOrder_releasesReservedProducts_andCreditsExistingWallet() {
        UUID orderId = UUID.randomUUID();
        product1.setStatus(ProductStatus.RESERVED);
        Order order = Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000001")
                .buyer(member)
                .status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("245000"))
                .build();

        OrderItem item1 = OrderItem.builder().id(UUID.randomUUID()).order(order).product(product1).build();
        Wallet existingWallet = Wallet.builder().id(UUID.randomUUID()).member(member)
                .balance(new BigDecimal("50000")).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of(item1));
        when(productRepository.findByIdForUpdate(product1.getId())).thenReturn(Optional.of(product1));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(existingWallet));
        when(platformFundRepository.save(any())).thenAnswer(invocation -> {
            PlatformFund saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        OrderCancelResponse response = orderService.cancelOrder(email, orderId);

        assertThat(response.getId()).isEqualTo(orderId.toString());
        assertThat(response.getStatus()).isEqualTo("CANCELLED");

        assertThat(product1.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        verify(productRepository).save(product1);

        assertThat(existingWallet.getBalance()).isEqualTo(new BigDecimal("295000"));
        verify(walletRepository).save(existingWallet);

        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, times(2)).save(txCaptor.capture());
        WalletTransaction memberTx = txCaptor.getAllValues().stream()
                .filter(tx -> tx.getWalletType() == WalletType.MEMBER).findFirst().orElseThrow();
        WalletTransaction platformTx = txCaptor.getAllValues().stream()
                .filter(tx -> tx.getWalletType() == WalletType.PLATFORM).findFirst().orElseThrow();

        assertThat(memberTx.getWalletId()).isEqualTo(existingWallet.getId());
        assertThat(memberTx.getType()).isEqualTo(WalletTransactionType.REFUND);
        assertThat(memberTx.getAmount()).isEqualTo(new BigDecimal("245000"));
        assertThat(memberTx.getRelatedOrder()).isEqualTo(order);
        assertThat(memberTx.getTransactionNo()).startsWith("WT-" + Year.now(ZoneOffset.UTC).getValue() + "-");
        assertThat(memberTx.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("295000"));
        assertThat(memberTx.getRelatedOrderItem()).isNull();

        assertThat(platformTx.getType()).isEqualTo(WalletTransactionType.REFUND);
        assertThat(platformTx.getAmount()).isEqualByComparingTo(new BigDecimal("-245000"));
        assertThat(platformTx.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("-245000"));
        assertThat(platformTx.getCounterpartTransaction()).isEqualTo(memberTx);
        assertThat(platformTx.getRelatedOrder()).isEqualTo(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo(CancelReason.BUYER_CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(order.getRefundAmount()).isEqualTo(new BigDecimal("245000"));
    }

    @Test
    void cancelOrder_createsNewWallet_whenMemberHasNoWalletYet() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000002")
                .buyer(member)
                .status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("100000"))
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of());
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        orderService.cancelOrder(email, orderId);

        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().getMember()).isEqualTo(member);
        assertThat(walletCaptor.getValue().getBalance()).isEqualTo(new BigDecimal("100000"));
    }

    @Test
    void cancelOrder_skipsProductsNotCurrentlyReserved() {
        UUID orderId = UUID.randomUUID();
        product1.setStatus(ProductStatus.SOLD);
        Order order = Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000003")
                .buyer(member)
                .status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("100000"))
                .build();
        OrderItem item1 = OrderItem.builder().id(UUID.randomUUID()).order(order).product(product1).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of(item1));
        when(productRepository.findByIdForUpdate(product1.getId())).thenReturn(Optional.of(product1));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        orderService.cancelOrder(email, orderId);

        verify(productRepository, never()).save(product1);
        assertThat(product1.getStatus()).isEqualTo(ProductStatus.SOLD);
    }

    @Test
    void confirmReceipt_throwsNotFound_whenAccountDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmReceipt(email, orderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");

        verify(orderRepository, never()).findById(any());
    }

    @Test
    void confirmReceipt_throwsNotFound_whenOrderDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmReceipt(email, orderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy đơn hàng.");
    }

    @Test
    void confirmReceipt_throwsForbidden_whenOrderBelongsToAnotherBuyer() {
        UUID orderId = UUID.randomUUID();
        Account anotherMember = Account.builder().id(UUID.randomUUID()).email("other@example.com").build();
        Order order = Order.builder().id(orderId).buyer(anotherMember).status(OrderStatus.DELIVERED).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmReceipt(email, orderId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không có quyền xác nhận đơn này.");

        verify(orderItemRepository, never()).findAllByOrder_Id(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void confirmReceipt_throwsConflictWithCode_whenOrderNotYetDelivered() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).buyer(member).status(OrderStatus.SHIPPING).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmReceipt(email, orderId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Đơn chưa ở trạng thái đã giao, không thể xác nhận nhận hàng.")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode())
                        .isEqualTo("ORDER_NOT_DELIVERED_YET"));

        verify(walletRepository, never()).save(any());
    }

    @Test
    void confirmReceipt_marksProductsSold_paysOutEachSeller_andCompletesOrder() {
        UUID orderId = UUID.randomUUID();
        Account seller1 = Account.builder().id(UUID.randomUUID()).email("seller1@example.com").build();
        Account seller2 = Account.builder().id(UUID.randomUUID()).email("seller2@example.com").build();
        product1.setStatus(ProductStatus.RESERVED);
        product1.setMember(seller1);
        product2.setStatus(ProductStatus.RESERVED);
        product2.setMember(seller2);

        Order order = Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000001")
                .buyer(member)
                .status(OrderStatus.DELIVERED)
                .build();

        OrderItem item1 = OrderItem.builder().id(UUID.randomUUID()).order(order).product(product1)
                .priceAtOrder(new BigDecimal("220000"))
                .commissionRateApplied(new BigDecimal("0.10"))
                .build();
        OrderItem item2 = OrderItem.builder().id(UUID.randomUUID()).order(order).product(product2)
                .priceAtOrder(new BigDecimal("380000"))
                .commissionRateApplied(new BigDecimal("0.15"))
                .build();

        Wallet seller1Wallet = Wallet.builder().id(UUID.randomUUID()).member(seller1)
                .balance(new BigDecimal("10000")).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of(item1, item2));
        when(productRepository.findByIdForUpdate(product1.getId())).thenReturn(Optional.of(product1));
        when(productRepository.findByIdForUpdate(product2.getId())).thenReturn(Optional.of(product2));
        when(walletRepository.findByMember_Id(seller1.getId())).thenReturn(Optional.of(seller1Wallet));
        when(walletRepository.findByMember_Id(seller2.getId())).thenReturn(Optional.empty());

        AtomicReference<PlatformFund> fundHolder = new AtomicReference<>();
        when(platformFundRepository.findTopByOrderByIdAsc())
                .thenAnswer(invocation -> Optional.ofNullable(fundHolder.get()));
        when(platformFundRepository.save(any())).thenAnswer(invocation -> {
            PlatformFund saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            fundHolder.set(saved);
            return saved;
        });

        OrderConfirmReceiptResponse response = orderService.confirmReceipt(email, orderId);

        assertThat(response.getId()).isEqualTo(orderId.toString());
        assertThat(response.getStatus()).isEqualTo("COMPLETED");

        assertThat(product1.getStatus()).isEqualTo(ProductStatus.SOLD);
        assertThat(product2.getStatus()).isEqualTo(ProductStatus.SOLD);
        verify(productRepository).save(product1);
        verify(productRepository).save(product2);

        // 220000 - 220000*0.10 = 198000; cộng vào ví có sẵn 10000 -> 208000
        assertThat(seller1Wallet.getBalance()).isEqualByComparingTo(new BigDecimal("208000"));

        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository, times(2)).save(walletCaptor.capture());
        Wallet seller2WalletSaved = walletCaptor.getAllValues().stream()
                .filter(w -> w.getMember().equals(seller2))
                .findFirst().orElseThrow();
        // 380000 - 380000*0.15 = 323000, ví mới tạo balance ban đầu = 0
        assertThat(seller2WalletSaved.getBalance()).isEqualByComparingTo(new BigDecimal("323000"));

        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, times(4)).save(txCaptor.capture());

        List<WalletTransaction> memberTxs = txCaptor.getAllValues().stream()
                .filter(tx -> tx.getWalletType() == WalletType.MEMBER).toList();
        List<WalletTransaction> platformTxs = txCaptor.getAllValues().stream()
                .filter(tx -> tx.getWalletType() == WalletType.PLATFORM).toList();
        assertThat(memberTxs).hasSize(2);
        assertThat(platformTxs).hasSize(2);

        assertThat(memberTxs)
                .allSatisfy(tx -> {
                    assertThat(tx.getType()).isEqualTo(WalletTransactionType.ORDER_PAYOUT);
                    assertThat(tx.getRelatedOrder()).isEqualTo(order);
                    assertThat(tx.getTransactionNo()).isNotBlank();
                    assertThat(tx.getBalanceAfter()).isNotNull();
                    assertThat(tx.getRelatedOrderItem()).isNotNull();
                });
        assertThat(memberTxs)
                .extracting(tx -> tx.getRelatedOrderItem().getProduct())
                .containsExactlyInAnyOrder(product1, product2);

        assertThat(platformTxs)
                .allSatisfy(tx -> {
                    assertThat(tx.getType()).isEqualTo(WalletTransactionType.ORDER_PAYOUT);
                    assertThat(tx.getRelatedOrder()).isEqualTo(order);
                    assertThat(tx.getCounterpartTransaction()).isIn(memberTxs);
                });
        assertThat(platformTxs)
                .extracting(WalletTransaction::getAmount)
                .usingElementComparator(java.util.Comparator.comparing(BigDecimal::doubleValue))
                .containsExactlyInAnyOrder(new BigDecimal("-198000"), new BigDecimal("-323000"));
        // quỹ nền tảng giảm dần theo từng lần chi trả cho seller: 0 -> -198000 -> -521000
        assertThat(fundHolder.get().getBalance()).isEqualByComparingTo(new BigDecimal("-521000"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getCompletedAt()).isNotNull();
    }

    @Test
    void confirmReceipt_stillPaysOutSeller_whenProductNoLongerReserved() {
        UUID orderId = UUID.randomUUID();
        Account seller = Account.builder().id(UUID.randomUUID()).email("seller@example.com").build();
        product1.setStatus(ProductStatus.SOLD);
        product1.setMember(seller);

        Order order = Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000002")
                .buyer(member)
                .status(OrderStatus.DELIVERED)
                .build();

        OrderItem item1 = OrderItem.builder().id(UUID.randomUUID()).order(order).product(product1)
                .priceAtOrder(new BigDecimal("100000"))
                .commissionRateApplied(new BigDecimal("0.10"))
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of(item1));
        when(productRepository.findByIdForUpdate(product1.getId())).thenReturn(Optional.of(product1));
        when(walletRepository.findByMember_Id(seller.getId())).thenReturn(Optional.empty());

        orderService.confirmReceipt(email, orderId);

        verify(productRepository, never()).save(product1);

        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, times(2)).save(txCaptor.capture());
        WalletTransaction memberTx = txCaptor.getAllValues().stream()
                .filter(tx -> tx.getWalletType() == WalletType.MEMBER).findFirst().orElseThrow();
        assertThat(memberTx.getAmount()).isEqualByComparingTo(new BigDecimal("90000"));
    }

    @Test
    void rateOrder_throwsNotFound_whenAccountDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.rateOrder(email, orderId, new RateOrderRequest((short) 5, "Tốt")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");

        verify(orderRepository, never()).findById(any());
    }

    @Test
    void rateOrder_throwsNotFound_whenOrderDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.rateOrder(email, orderId, new RateOrderRequest((short) 5, "Tốt")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy đơn hàng.");
    }

    @Test
    void rateOrder_throwsForbidden_whenOrderBelongsToAnotherBuyer() {
        UUID orderId = UUID.randomUUID();
        Account anotherMember = Account.builder().id(UUID.randomUUID()).email("other@example.com").build();
        Order order = Order.builder().id(orderId).buyer(anotherMember).status(OrderStatus.COMPLETED).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.rateOrder(email, orderId, new RateOrderRequest((short) 5, "Tốt")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không có quyền đánh giá đơn này.");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void rateOrder_throwsBadRequest_whenServiceRatingIsNull() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).buyer(member).status(OrderStatus.COMPLETED).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.rateOrder(email, orderId, new RateOrderRequest(null, "Tốt")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("serviceRating phải từ 1 đến 5.");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void rateOrder_throwsBadRequest_whenServiceRatingOutOfRange() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).buyer(member).status(OrderStatus.COMPLETED).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.rateOrder(email, orderId, new RateOrderRequest((short) 6, "Tốt")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("serviceRating phải từ 1 đến 5.");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void rateOrder_throwsConflictWithCode_whenOrderNotCompleted() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).buyer(member).status(OrderStatus.SHIPPING).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.rateOrder(email, orderId, new RateOrderRequest((short) 5, "Tốt")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Chỉ đánh giá đơn đã hoàn tất.")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode())
                        .isEqualTo("ORDER_NOT_COMPLETED"));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void rateOrder_savesServiceRatingAndComment_whenOrderCompleted() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).buyer(member).status(OrderStatus.COMPLETED).build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderRatingResponse response = orderService.rateOrder(
                email, orderId, new RateOrderRequest((short) 5, "Đóng gói cẩn thận, giao nhanh"));

        assertThat(response.getId()).isEqualTo(orderId.toString());
        assertThat(response.getServiceRating()).isEqualTo((short) 5);
        assertThat(response.getComment()).isEqualTo("Đóng gói cẩn thận, giao nhanh");

        assertThat(order.getServiceRating()).isEqualTo((short) 5);
        assertThat(order.getRatingComment()).isEqualTo("Đóng gói cẩn thận, giao nhanh");
        assertThat(order.getRatedAt()).isNotNull();
        verify(orderRepository).save(order);
    }

    @Test
    void rateOrder_allowsOverwritingExistingRating() {
        UUID orderId = UUID.randomUUID();
        Instant firstRatedAt = Instant.parse("2026-01-01T00:00:00Z");
        Order order = Order.builder().id(orderId).buyer(member).status(OrderStatus.COMPLETED)
                .serviceRating((short) 2)
                .ratingComment("Giao chậm")
                .ratedAt(firstRatedAt)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderRatingResponse response = orderService.rateOrder(
                email, orderId, new RateOrderRequest((short) 5, "Sửa lại, giao ổn"));

        assertThat(response.getServiceRating()).isEqualTo((short) 5);
        assertThat(response.getComment()).isEqualTo("Sửa lại, giao ổn");
        assertThat(order.getRatedAt()).isNotEqualTo(firstRatedAt);
    }

    // ---------------------------------------------------------------- F-062: tự hủy đơn khi GHN lỗi

    private Order carrierFailureOrder(UUID orderId, OrderStatus status) {
        return Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000777")
                .buyer(member)
                .status(status)
                .totalAmount(new BigDecimal("245000"))
                .build();
    }

    @Test
    void cancelOrderForCarrierFailure_doesNothing_whenOrderNoLongerExists() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        orderService.cancelOrderForCarrierFailure(orderId, "lost");

        verify(orderItemRepository, never()).findAllByOrder_Id(any());
        verify(orderRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
    }

    // Webhook GHN có thể bắn trùng hoặc đến muộn: chỉ đơn đang PACKING/SHIPPING mới được hủy + hoàn tiền,
    // nếu không đơn đã giao/đã hoàn tất/đã hủy sẽ bị hoàn tiền lần hai.
    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PACKING", "SHIPPING"}, mode = EnumSource.Mode.EXCLUDE)
    void cancelOrderForCarrierFailure_doesNothing_whenOrderIsNotInTransit(OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        Order order = carrierFailureOrder(orderId, status);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderService.cancelOrderForCarrierFailure(orderId, "lost");

        assertThat(order.getStatus()).isEqualTo(status);
        assertThat(order.getRefundAmount()).isNull();
        verify(orderItemRepository, never()).findAllByOrder_Id(any());
        verify(orderRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
    }

    @Test
    void cancelOrderForCarrierFailure_releasesProductsAndRefundsBuyer_whenOrderIsShipping() {
        UUID orderId = UUID.randomUUID();
        product1.setStatus(ProductStatus.RESERVED);
        Order order = carrierFailureOrder(orderId, OrderStatus.SHIPPING);
        OrderItem item1 = OrderItem.builder().id(UUID.randomUUID()).order(order).product(product1).build();
        Wallet existingWallet = Wallet.builder().id(UUID.randomUUID()).member(member)
                .balance(new BigDecimal("50000")).build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of(item1));
        when(productRepository.findByIdForUpdate(product1.getId())).thenReturn(Optional.of(product1));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(existingWallet));
        when(platformFundRepository.save(any())).thenAnswer(invocation -> {
            PlatformFund saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        orderService.cancelOrderForCarrierFailure(orderId, "Thất lạc trong quá trình vận chuyển");

        // Hàng chưa giao được thì nhả lại lên sàn
        assertThat(product1.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        verify(productRepository).save(product1);

        // Hoàn 100% tổng tiền đơn vào ví người mua
        assertThat(existingWallet.getBalance()).isEqualTo(new BigDecimal("295000"));
        verify(walletRepository).save(existingWallet);

        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, times(2)).save(txCaptor.capture());
        WalletTransaction memberTx = txCaptor.getAllValues().stream()
                .filter(tx -> tx.getWalletType() == WalletType.MEMBER).findFirst().orElseThrow();
        WalletTransaction platformTx = txCaptor.getAllValues().stream()
                .filter(tx -> tx.getWalletType() == WalletType.PLATFORM).findFirst().orElseThrow();

        assertThat(memberTx.getWalletId()).isEqualTo(existingWallet.getId());
        assertThat(memberTx.getType()).isEqualTo(WalletTransactionType.REFUND);
        assertThat(memberTx.getAmount()).isEqualByComparingTo(new BigDecimal("245000"));
        assertThat(memberTx.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("295000"));
        assertThat(memberTx.getRelatedOrder()).isEqualTo(order);
        assertThat(memberTx.getTransactionNo()).startsWith("WT-" + Year.now(ZoneOffset.UTC).getValue() + "-");
        assertThat(memberTx.getNote()).isEqualTo(
                "Hoàn tiền do vận chuyển GHN lỗi - đơn OD-2026-000777 (Thất lạc trong quá trình vận chuyển)");

        // Bút toán đối ứng của quỹ nền tảng, ngược dấu (F-039)
        assertThat(platformTx.getType()).isEqualTo(WalletTransactionType.REFUND);
        assertThat(platformTx.getAmount()).isEqualByComparingTo(new BigDecimal("-245000"));
        assertThat(platformTx.getCounterpartTransaction()).isEqualTo(memberTx);
        assertThat(platformTx.getRelatedOrder()).isEqualTo(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo(CancelReason.CARRIER_FAILED);
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(order.getRefundAmount()).isEqualByComparingTo(new BigDecimal("245000"));
        verify(orderRepository).save(order);
    }

    @Test
    void cancelOrderForCarrierFailure_alsoCancels_whenOrderIsStillPacking() {
        UUID orderId = UUID.randomUUID();
        Order order = carrierFailureOrder(orderId, OrderStatus.PACKING);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of());
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        orderService.cancelOrderForCarrierFailure(orderId, "Hủy vận đơn");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo(CancelReason.CARRIER_FAILED);
        assertThat(order.getRefundAmount()).isEqualByComparingTo(new BigDecimal("245000"));
        verify(orderRepository).save(order);
    }

    @Test
    void cancelOrderForCarrierFailure_omitsReasonFromNote_whenReasonIsBlank() {
        UUID orderId = UUID.randomUUID();
        Order order = carrierFailureOrder(orderId, OrderStatus.SHIPPING);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of());
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        orderService.cancelOrderForCarrierFailure(orderId, null);

        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, times(2)).save(txCaptor.capture());
        WalletTransaction memberTx = txCaptor.getAllValues().stream()
                .filter(tx -> tx.getWalletType() == WalletType.MEMBER).findFirst().orElseThrow();

        assertThat(memberTx.getNote()).isEqualTo("Hoàn tiền do vận chuyển GHN lỗi - đơn OD-2026-000777");
    }

    @Test
    void cancelOrderForCarrierFailure_createsNewWallet_whenBuyerHasNoWalletYet() {
        UUID orderId = UUID.randomUUID();
        Order order = carrierFailureOrder(orderId, OrderStatus.SHIPPING);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of());
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        orderService.cancelOrderForCarrierFailure(orderId, "lost");

        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().getMember()).isEqualTo(member);
        assertThat(walletCaptor.getValue().getBalance()).isEqualByComparingTo(new BigDecimal("245000"));
    }

    @Test
    void cancelOrderForCarrierFailure_skipsProductsNotCurrentlyReserved() {
        UUID orderId = UUID.randomUUID();
        product1.setStatus(ProductStatus.SOLD);
        Order order = carrierFailureOrder(orderId, OrderStatus.SHIPPING);
        OrderItem item1 = OrderItem.builder().id(UUID.randomUUID()).order(order).product(product1).build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of(item1));
        when(productRepository.findByIdForUpdate(product1.getId())).thenReturn(Optional.of(product1));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        orderService.cancelOrderForCarrierFailure(orderId, "lost");

        verify(productRepository, never()).save(any());
        assertThat(product1.getStatus()).isEqualTo(ProductStatus.SOLD);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ==================== loadPayableOrders (P3.5) ====================

    private Order pendingOrder(UUID orderId, String orderNo, String totalAmount) {
        return Order.builder()
                .id(orderId)
                .orderNo(orderNo)
                .buyer(member)
                .totalAmount(new BigDecimal(totalAmount))
                .status(OrderStatus.PENDING_PAYMENT)
                .build();
    }

    @Test
    void loadPayableOrders_throwsOrderAlreadyPaid_whenOrderIsPaid() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingOrder(orderId, "OD-2026-000001", "150000");
        order.setStatus(OrderStatus.PAID);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.loadPayableOrders(email, List.of(orderId)))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("ORDER_ALREADY_PAID"));
    }

    @Test
    void loadPayableOrders_throwsOrderNotPending_whenOrderAlreadyCancelled() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingOrder(orderId, "OD-2026-000001", "150000");
        order.setStatus(OrderStatus.CANCELLED);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.loadPayableOrders(email, List.of(orderId)))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("ORDER_NOT_PENDING"));
    }

    @Test
    void loadPayableOrders_throwsForbidden_whenOrderBelongsToSomeoneElse() {
        UUID orderId = UUID.randomUUID();
        Account otherBuyer = Account.builder().id(UUID.randomUUID()).email("other@example.com").build();
        Order order = pendingOrder(orderId, "OD-2026-000001", "150000");
        order.setBuyer(otherBuyer);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.loadPayableOrders(email, List.of(orderId)))
                .isInstanceOf(ForbiddenException.class);
    }

    // ==================== payOrders (P3.3) ====================

    @Test
    void payOrders_rejectsMethodOtherThanWallet() {
        PayOrdersRequest request = new PayOrdersRequest(List.of(UUID.randomUUID().toString()), "MOMO");

        assertThatThrownBy(() -> orderService.payOrders(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("WALLET");

        verify(walletRepository, never()).save(any());
    }

    @Test
    void payOrders_throwsInsufficientBalance_whenAvailableBalanceTooLow() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingOrder(orderId, "OD-2026-000001", "150000");

        // Số dư 200k nhưng đang khóa 100k cho yêu cầu rút -> khả dụng chỉ 100k
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(member)
                .balance(new BigDecimal("200000"))
                .lockedBalance(new BigDecimal("100000"))
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(walletRepository.findByMemberIdForUpdate(member.getId())).thenReturn(Optional.of(wallet));

        PayOrdersRequest request = new PayOrdersRequest(List.of(orderId.toString()), "WALLET");

        assertThatThrownBy(() -> orderService.payOrders(email, request))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("INSUFFICIENT_BALANCE"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
    }

    @Test
    void payOrders_debitsWalletAndMarksOrdersPaid() {
        UUID firstOrderId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();
        Order firstOrder = pendingOrder(firstOrderId, "OD-2026-000001", "150000");
        Order secondOrder = pendingOrder(secondOrderId, "OD-2026-000002", "50000");

        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(member)
                .balance(new BigDecimal("500000"))
                .lockedBalance(BigDecimal.ZERO)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(firstOrderId)).thenReturn(Optional.of(firstOrder));
        when(orderRepository.findById(secondOrderId)).thenReturn(Optional.of(secondOrder));
        when(walletRepository.findByMemberIdForUpdate(member.getId())).thenReturn(Optional.of(wallet));
        when(platformFundRepository.findTopByOrderByIdAsc())
                .thenReturn(Optional.of(PlatformFund.builder().id(UUID.randomUUID())
                        .balance(BigDecimal.ZERO).build()));

        PayOrdersRequest request = new PayOrdersRequest(
                List.of(firstOrderId.toString(), secondOrderId.toString()), "WALLET");

        PayOrdersResponse response = orderService.payOrders(email, request);

        assertThat(response.getTotalAmount()).isEqualByComparingTo("200000");
        assertThat(response.getWalletBalance()).isEqualByComparingTo("300000");
        assertThat(response.getOrders()).hasSize(2);

        assertThat(firstOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(secondOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(firstOrder.getPaymentMethod()).isEqualTo(OrderPaymentMethod.WALLET);
        assertThat(wallet.getBalance()).isEqualByComparingTo("300000");

        // Mỗi đơn một bút toán ví Member + một bút toán đối ứng quỹ nền tảng (ghi sổ kép F-039)
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, times(4)).save(captor.capture());
        List<WalletTransaction> memberTransactions = captor.getAllValues().stream()
                .filter(tx -> tx.getWalletType() == WalletType.MEMBER)
                .toList();
        assertThat(memberTransactions).hasSize(2);
        assertThat(memberTransactions).allSatisfy(tx ->
                assertThat(tx.getType()).isEqualTo(WalletTransactionType.ORDER_PAYMENT));
        // Tiền ra khỏi ví nên bút toán phải âm
        assertThat(memberTransactions).extracting(WalletTransaction::getAmount)
                .containsExactly(new BigDecimal("-150000"), new BigDecimal("-50000"));
    }

    @Test
    void payOrders_doesNotChargeTwice_whenSameOrderIdRepeated() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingOrder(orderId, "OD-2026-000001", "150000");

        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(member)
                .balance(new BigDecimal("500000"))
                .lockedBalance(BigDecimal.ZERO)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(walletRepository.findByMemberIdForUpdate(member.getId())).thenReturn(Optional.of(wallet));
        when(platformFundRepository.findTopByOrderByIdAsc())
                .thenReturn(Optional.of(PlatformFund.builder().id(UUID.randomUUID())
                        .balance(BigDecimal.ZERO).build()));

        PayOrdersRequest request = new PayOrdersRequest(
                List.of(orderId.toString(), orderId.toString()), "WALLET");

        PayOrdersResponse response = orderService.payOrders(email, request);

        assertThat(response.getTotalAmount()).isEqualByComparingTo("150000");
        assertThat(wallet.getBalance()).isEqualByComparingTo("350000");
    }

    // ==================== applyGatewayPaymentSuccess (P3.2) ====================

    @Test
    void applyGatewayPaymentSuccess_marksPaid_whenOrderStillPendingPayment() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingOrder(orderId, "OD-2026-000001", "150000");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderService.applyGatewayPaymentSuccess(orderId, OrderPaymentMethod.MOMO, "note");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentMethod()).isEqualTo(OrderPaymentMethod.MOMO);
        verify(walletRepository, never()).save(any());
    }

    // Đây là bug P3.2: IPN về muộn sau khi đơn tự hủy vì quá hạn thanh toán. Hàng đã nhả về sàn
    // nên tuyệt đối không được chốt lại đơn, tiền phải vào ví người mua.
    @Test
    void applyGatewayPaymentSuccess_refundsToWallet_whenOrderAlreadyCancelled() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingOrder(orderId, "OD-2026-000001", "150000");
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(CancelReason.PAYMENT_EXPIRED);

        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(member)
                .balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO).build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        when(platformFundRepository.findTopByOrderByIdAsc())
                .thenReturn(Optional.of(PlatformFund.builder().id(UUID.randomUUID())
                        .balance(BigDecimal.ZERO).build()));

        orderService.applyGatewayPaymentSuccess(orderId, OrderPaymentMethod.MOMO, "Hoàn tiền MoMo về muộn");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getRefundAmount()).isEqualByComparingTo("150000");
        assertThat(wallet.getBalance()).isEqualByComparingTo("150000");

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getType()).isEqualTo(WalletTransactionType.REFUND);
    }

    @Test
    void applyGatewayPaymentSuccess_doesNothing_whenSameGatewayAlreadySettledOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingOrder(orderId, "OD-2026-000001", "150000");
        order.setStatus(OrderStatus.PAID);
        order.setPaymentMethod(OrderPaymentMethod.MOMO);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderService.applyGatewayPaymentSuccess(orderId, OrderPaymentMethod.MOMO, "note");

        assertThat(order.getRefundAmount()).isNull();
        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
    }

    // Người mua bấm trả MoMo rồi đổi ý trả bằng ví trước khi IPN về: tiền MoMo vẫn về thật
    @Test
    void applyGatewayPaymentSuccess_refundsToWallet_whenOrderAlreadyPaidByAnotherMethod() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingOrder(orderId, "OD-2026-000001", "150000");
        order.setStatus(OrderStatus.PAID);
        order.setPaymentMethod(OrderPaymentMethod.WALLET);

        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(member)
                .balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO).build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        when(platformFundRepository.findTopByOrderByIdAsc())
                .thenReturn(Optional.of(PlatformFund.builder().id(UUID.randomUUID())
                        .balance(BigDecimal.ZERO).build()));

        orderService.applyGatewayPaymentSuccess(orderId, OrderPaymentMethod.MOMO, "Hoàn tiền MoMo trùng");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(wallet.getBalance()).isEqualByComparingTo("150000");
    }

    // ==================== getOrderDetail: field cho màn chi tiết đơn (P4.1) ====================

    @Test
    void getOrderDetail_exposesPaymentDeadlineWarehouseIdAndReturnInfo() {
        UUID orderId = UUID.randomUUID();
        UUID returnRequestId = UUID.randomUUID();
        Instant paymentDeadline = Instant.parse("2026-01-01T00:15:00Z");
        Instant deliveredAt = Instant.parse("2026-01-05T00:00:00Z");

        Order order = Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000001")
                .buyer(member)
                .warehouse(warehouse1)
                .status(OrderStatus.DELIVERED)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .paymentMethod(OrderPaymentMethod.MOMO)
                .subtotal(new BigDecimal("600000"))
                .shippingFee(new BigDecimal("25000"))
                .totalAmount(new BigDecimal("625000"))
                .paymentDeadline(paymentDeadline)
                .deliveredAt(deliveredAt)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(orderId)).thenReturn(List.of());
        when(productPhotoRepository.findByProduct_IdInOrderByIdAsc(any())).thenReturn(List.of());
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.empty());
        when(returnRequestRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId))
                .thenReturn(Optional.of(ReturnRequest.builder().id(returnRequestId).order(order).build()));

        OrderDetailResponse result = orderService.getOrderDetail(email, orderId);

        assertThat(result.getPaymentDeadline()).isEqualTo(paymentDeadline);
        assertThat(result.getWarehouseId()).isEqualTo(warehouse1.getId().toString());
        assertThat(result.getReturnRequestId()).isEqualTo(returnRequestId.toString());
        // 3 ngày kể từ lúc GHN báo đã giao, khớp app.return.request-window-days
        assertThat(result.getReturnDeadline()).isEqualTo(deliveredAt.plus(3, ChronoUnit.DAYS));
    }

    @Test
    void getOrderDetail_returnFieldsAreNull_whenOrderNotDeliveredYet() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000001")
                .buyer(member)
                .warehouse(warehouse1)
                .status(OrderStatus.PENDING_PAYMENT)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .paymentMethod(OrderPaymentMethod.MOMO)
                .subtotal(new BigDecimal("600000"))
                .shippingFee(new BigDecimal("25000"))
                .totalAmount(new BigDecimal("625000"))
                .paymentDeadline(Instant.parse("2026-01-01T00:15:00Z"))
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(orderId)).thenReturn(List.of());
        when(productPhotoRepository.findByProduct_IdInOrderByIdAsc(any())).thenReturn(List.of());
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.empty());
        when(returnRequestRepository.findFirstByOrder_IdOrderByCreatedAtDesc(orderId))
                .thenReturn(Optional.empty());

        OrderDetailResponse result = orderService.getOrderDetail(email, orderId);

        assertThat(result.getReturnRequestId()).isNull();
        // Chưa giao thì chưa có mốc nào để tính hạn trả hàng
        assertThat(result.getReturnDeadline()).isNull();
    }

    // ==================== getTracking: màn theo dõi vận chuyển (P4.2) ====================

    private Order shippingOrder(UUID orderId, Instant expectedDeliveryDate) {
        return Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000001")
                .buyer(member)
                .warehouse(warehouse1)
                .status(OrderStatus.SHIPPING)
                .ghnTrackingCode("GHN123456789")
                .expectedDeliveryDate(expectedDeliveryDate)
                .build();
    }

    private OrderTrackingEvent trackingEvent(Order order, String status, String description,
                                              String location, Instant occurredAt) {
        return OrderTrackingEvent.builder()
                .id(UUID.randomUUID())
                .order(order)
                .ghnStatus(status)
                .description(description)
                .location(location)
                .occurredAt(occurredAt)
                .build();
    }

    @Test
    void getTracking_mapsExpectedDeliveryDateAndEventLocation() {
        UUID orderId = UUID.randomUUID();
        Instant expected = Instant.parse("2026-01-07T10:00:00Z");
        Instant firstAt = Instant.parse("2026-01-05T08:00:00Z");
        Instant secondAt = Instant.parse("2026-01-06T09:00:00Z");

        Order order = shippingOrder(orderId, expected);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderTrackingEventRepository.findAllByOrder_IdOrderByOccurredAtAsc(orderId))
                .thenReturn(List.of(
                        trackingEvent(order, "picked", "Đã lấy hàng", "Kho Quận 7", firstAt),
                        trackingEvent(order, "delivering", "Đang giao", "Kho Bình Thạnh", secondAt)));

        OrderTrackingResponse result = orderService.getTracking(email, orderId);

        assertThat(result.getGhnTrackingCode()).isEqualTo("GHN123456789");
        assertThat(result.getExpectedDeliveryDate()).isEqualTo(expected);
        assertThat(result.getHasIssue()).isFalse();
        assertThat(result.getIssueNote()).isNull();

        assertThat(result.getEvents()).hasSize(2);
        assertThat(result.getEvents().get(0).getStatus()).isEqualTo("picked");
        assertThat(result.getEvents().get(0).getLocation()).isEqualTo("Kho Quận 7");
        assertThat(result.getEvents().get(0).getTimestamp()).isEqualTo(firstAt);
        assertThat(result.getEvents().get(1).getTimestamp()).isEqualTo(secondAt);
    }

    @Test
    void getTracking_flagsIssue_whenLatestEventIsDeliveryFail() {
        UUID orderId = UUID.randomUUID();
        Order order = shippingOrder(orderId, null);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderTrackingEventRepository.findAllByOrder_IdOrderByOccurredAtAsc(orderId))
                .thenReturn(List.of(
                        trackingEvent(order, "delivering", "Đang giao", null,
                                Instant.parse("2026-01-05T08:00:00Z")),
                        trackingEvent(order, "delivery_fail", "Khách không nghe máy", null,
                                Instant.parse("2026-01-06T09:00:00Z"))));

        OrderTrackingResponse result = orderService.getTracking(email, orderId);

        assertThat(result.getHasIssue()).isTrue();
        assertThat(result.getIssueNote()).isEqualTo("Khách không nghe máy");
    }

    // Giao hụt rồi giao lại thành công thì không còn là sự cố nữa - chỉ xét sự kiện mới nhất
    @Test
    void getTracking_clearsIssue_whenRedeliverySucceededAfterFailure() {
        UUID orderId = UUID.randomUUID();
        Order order = shippingOrder(orderId, null);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderTrackingEventRepository.findAllByOrder_IdOrderByOccurredAtAsc(orderId))
                .thenReturn(List.of(
                        trackingEvent(order, "delivery_fail", "Khách không nghe máy", null,
                                Instant.parse("2026-01-05T08:00:00Z")),
                        trackingEvent(order, "delivered", "Giao thành công", null,
                                Instant.parse("2026-01-06T09:00:00Z"))));

        OrderTrackingResponse result = orderService.getTracking(email, orderId);

        assertThat(result.getHasIssue()).isFalse();
        assertThat(result.getIssueNote()).isNull();
    }

    @Test
    void getTracking_hasIssueIsFalse_whenNoEventYet() {
        UUID orderId = UUID.randomUUID();
        Order order = shippingOrder(orderId, null);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderTrackingEventRepository.findAllByOrder_IdOrderByOccurredAtAsc(orderId))
                .thenReturn(List.of());

        OrderTrackingResponse result = orderService.getTracking(email, orderId);

        assertThat(result.getHasIssue()).isFalse();
        assertThat(result.getEvents()).isEmpty();
    }

    // ==================== getSoldOrders ====================

    /** Đơn của người mua khác, chứa hàng do `seller` ký gửi. */
    private Order soldOrder(UUID orderId, OrderStatus status) {
        return Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000777")
                .buyer(Account.builder().id(UUID.randomUUID()).email("buyer@example.com").build())
                .warehouse(warehouse1)
                .status(status)
                .totalAmount(new BigDecimal("250000"))
                .createdAt(Instant.parse("2026-10-01T00:00:00Z"))
                .build();
    }

    private OrderItem soldItem(Order order, Product product, String price, String commissionRate) {
        return OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .product(product)
                .priceAtOrder(new BigDecimal(price))
                .commissionRateApplied(new BigDecimal(commissionRate))
                .taxRateApplied(BigDecimal.ZERO)
                .termApplied((short) 1)
                .build();
    }

    @Test
    void getSoldOrders_defaultStatuses_excludePendingPaymentAndCancelled() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findOrdersContainingSellerItems(eq(member.getId()), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        orderService.getSoldOrders(email, null, 0, 20);

        ArgumentCaptor<List<OrderStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).findOrdersContainingSellerItems(
                eq(member.getId()), statuses.capture(), any(Pageable.class));

        assertThat(statuses.getValue())
                .containsExactly(OrderStatus.PAID, OrderStatus.PACKING, OrderStatus.SHIPPING,
                        OrderStatus.DELIVERED, OrderStatus.COMPLETED, OrderStatus.RETURNED)
                .doesNotContain(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED);
    }

    @Test
    void getSoldOrders_explicitStatusIsHonoured() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findOrdersContainingSellerItems(eq(member.getId()), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        orderService.getSoldOrders(email, "CANCELLED", 0, 20);

        ArgumentCaptor<List<OrderStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).findOrdersContainingSellerItems(
                eq(member.getId()), statuses.capture(), any(Pageable.class));

        assertThat(statuses.getValue()).containsExactly(OrderStatus.CANCELLED);
    }

    @Test
    void getSoldOrders_throwsBadRequest_whenStatusIsNotAValidOrderStatus() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> orderService.getSoldOrders(email, "NOPE", 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("status không hợp lệ: NOPE");

        verify(orderRepository, never()).findOrdersContainingSellerItems(any(), any(), any());
    }

    @Test
    void getSoldOrders_skipsItemQuery_whenNoOrdersFound() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findOrdersContainingSellerItems(eq(member.getId()), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        SoldOrderListResponse result = orderService.getSoldOrders(email, null, 0, 20);

        assertThat(result.getOrders().getContent()).isEmpty();
        verify(orderItemRepository, never()).findSellerItemsByOrderIds(any(), any());
    }

    // Tiền hiển thị phải khớp công thức payout của confirmReceipt: giá - hoa hồng
    @Test
    void getSoldOrders_sumsOnlySellerItems_andComputesNetAmount() {
        UUID orderId = UUID.randomUUID();
        Order order = soldOrder(orderId, OrderStatus.SHIPPING);
        OrderItem mine = soldItem(order, product1, "220000", "0.10");

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findOrdersContainingSellerItems(eq(member.getId()), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(orderItemRepository.findSellerItemsByOrderIds(List.of(orderId), member.getId()))
                .thenReturn(List.of(mine));
        when(productPhotoRepository.findByProduct_IdInOrderByIdAsc(List.of("Q7-AK-000845")))
                .thenReturn(List.of());

        SoldOrderListResponse result = orderService.getSoldOrders(email, null, 0, 20);

        assertThat(result.getOrders().getContent()).hasSize(1);
        SoldOrderResponse sold = result.getOrders().getContent().get(0);

        assertThat(sold.getOrderId()).isEqualTo(orderId.toString());
        assertThat(sold.getStatus()).isEqualTo("SHIPPING");
        assertThat(sold.getWarehouseName()).isEqualTo("Kho Quận 7");
        assertThat(sold.getItemCount()).isEqualTo(1);
        // Đơn 250000 nhưng món của người bán chỉ 220000 - subtotal là của người bán, không của đơn
        assertThat(sold.getSubtotal()).isEqualByComparingTo("220000");
        assertThat(sold.getNetAmount()).isEqualByComparingTo("198000");
        assertThat(sold.getPaidOut()).isFalse();
        assertThat(sold.getItems()).hasSize(1);
        assertThat(sold.getItems().get(0).getCommission()).isEqualByComparingTo("22000");
        assertThat(sold.getItems().get(0).getNetAmount()).isEqualByComparingTo("198000");
    }

    @Test
    void getSoldOrders_marksPaidOut_whenOrderIsCompleted() {
        UUID orderId = UUID.randomUUID();
        Order order = soldOrder(orderId, OrderStatus.COMPLETED);
        order.setCompletedAt(Instant.parse("2026-10-05T00:00:00Z"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(orderRepository.findOrdersContainingSellerItems(eq(member.getId()), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(orderItemRepository.findSellerItemsByOrderIds(List.of(orderId), member.getId()))
                .thenReturn(List.of(soldItem(order, product1, "220000", "0.10")));
        when(productPhotoRepository.findByProduct_IdInOrderByIdAsc(List.of("Q7-AK-000845")))
                .thenReturn(List.of());

        SoldOrderListResponse result = orderService.getSoldOrders(email, null, 0, 20);

        SoldOrderResponse sold = result.getOrders().getContent().get(0);
        assertThat(sold.getPaidOut()).isTrue();
        assertThat(sold.getCompletedAt()).isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
    }

    @Test
    void applyGatewayPaymentSuccess_doesNotRefundTwice_whenOrderAlreadyRefunded() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingOrder(orderId, "OD-2026-000001", "150000");
        order.setStatus(OrderStatus.CANCELLED);
        order.setRefundAmount(new BigDecimal("150000"));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderService.applyGatewayPaymentSuccess(orderId, OrderPaymentMethod.MOMO, "note");

        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
    }
}
