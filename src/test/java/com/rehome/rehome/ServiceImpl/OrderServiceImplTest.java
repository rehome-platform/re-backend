package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.response.OrderListResponse;
import com.rehome.rehome.dto.response.OrderResponse;
import com.rehome.rehome.dto.response.OrderSummaryResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Cart;
import com.rehome.rehome.entity.marketplace.CartItem;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CartItemRepository;
import com.rehome.rehome.repository.CartRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    @Mock ProductPhotoRepository productPhotoRepository;

    @InjectMocks
    OrderServiceImpl orderService;

    Account member;
    String email;
    ShippingAddress address;
    Warehouse warehouse1;
    Warehouse warehouse2;
    ConsignmentReceipt receipt;
    Product product1;
    Product product2;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(orderService, "shippingFee", new BigDecimal("25000"));
        ReflectionTestUtils.setField(orderService, "paymentDeadlineMinutes", 15L);
        ReflectionTestUtils.setField(orderService, "taxRate", BigDecimal.ZERO);

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

        product1 = Product.builder()
                .id("Q7-AK-000845")
                .status(ProductStatus.ON_SALE)
                .currentPrice(new BigDecimal("220000"))
                .currentTerm((short) 1)
                .warehouse(warehouse1)
                .consignmentReceipt(receipt)
                .build();

        product2 = Product.builder()
                .id("Q7-AK-000912")
                .status(ProductStatus.ON_SALE)
                .currentPrice(new BigDecimal("380000"))
                .currentTerm((short) 1)
                .warehouse(warehouse2)
                .consignmentReceipt(receipt)
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

    @Test
    void createOrders_createsSingleOrder_forBuyNowFlow_andReservesProduct() {
        CreateOrderRequest request = new CreateOrderRequest(address.getId().toString(), List.of("Q7-AK-000845"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(shippingAddressRepository.findById(address.getId())).thenReturn(Optional.of(address));
        when(productRepository.findByIdForUpdate("Q7-AK-000845")).thenReturn(Optional.of(product1));
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
        stubOrderSaveAssignsId();

        orderService.createOrders(email, request);

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getCommissionRateApplied()).isEqualTo(new BigDecimal("0.15"));
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
}
