package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.response.OrderResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Cart;
import com.rehome.rehome.entity.marketplace.CartItem;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CartItemRepository;
import com.rehome.rehome.repository.CartRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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

        address = ShippingAddress.builder().id(UUID.randomUUID()).member(member).build();

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
}
