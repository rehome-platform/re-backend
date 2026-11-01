package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.CancelReason;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderCancelExecutorTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderItemRepository orderItemRepository;

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    OrderCancelExecutor executor;

    UUID orderId;
    Order order;
    Product product1;
    Product product2;
    OrderItem item1;
    OrderItem item2;

    @BeforeEach
    void setup() {
        orderId = UUID.randomUUID();

        order = Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000008")
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentDeadline(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();

        product1 = Product.builder().id("Q7-AK-000845").status(ProductStatus.RESERVED).build();
        product2 = Product.builder().id("Q7-AK-000846").status(ProductStatus.RESERVED).build();

        item1 = OrderItem.builder().order(order).product(product1).build();
        item2 = OrderItem.builder().order(order).product(product2).build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of(item1, item2));
        when(productRepository.findByIdForUpdate(product1.getId())).thenReturn(Optional.of(product1));
        when(productRepository.findByIdForUpdate(product2.getId())).thenReturn(Optional.of(product2));
    }

    // ==================== đường chính ====================

    @Test
    @DisplayName("cancelOrder: đơn quá hạn -> nhả hàng RESERVED về ON_SALE và huỷ đơn PAYMENT_EXPIRED")
    void cancelOrder_expiredOrder_releasesProductsAndCancelsOrder() {
        executor.cancelOrder(orderId);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(2)).save(productCaptor.capture());
        assertThat(productCaptor.getAllValues())
                .allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(ProductStatus.ON_SALE));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(saved.getCancelReason()).isEqualTo(CancelReason.PAYMENT_EXPIRED);
        assertThat(saved.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("cancelOrder: khoá từng sản phẩm bằng findByIdForUpdate (PESSIMISTIC_WRITE) theo id của OrderItem")
    void cancelOrder_locksEachProductForUpdate() {
        executor.cancelOrder(orderId);

        verify(productRepository).findByIdForUpdate(product1.getId());
        verify(productRepository).findByIdForUpdate(product2.getId());
    }

    @Test
    @DisplayName("cancelOrder: đơn không có item nào -> vẫn huỷ đơn, không động tới product")
    void cancelOrder_noItems_stillCancelsOrder() {
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of());

        executor.cancelOrder(orderId);

        verify(productRepository, never()).findByIdForUpdate(any());
        verify(productRepository, never()).save(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
    }

    // ==================== nhả hàng có điều kiện ====================

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = "RESERVED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("cancelOrder: sản phẩm không còn RESERVED -> không đổi trạng thái, không save, nhưng đơn vẫn bị huỷ")
    void cancelOrder_productNotReserved_isNotTouched(ProductStatus status) {
        product1.setStatus(status);

        executor.cancelOrder(orderId);

        assertThat(product1.getStatus()).isEqualTo(status);
        verify(productRepository, never()).save(product1);
        // product2 vẫn là RESERVED nên được nhả
        verify(productRepository).save(product2);
        assertThat(product2.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelOrder: sản phẩm đã bị xoá (findByIdForUpdate rỗng) -> bỏ qua, vẫn huỷ đơn")
    void cancelOrder_productMissing_isSkipped() {
        when(productRepository.findByIdForUpdate(product1.getId())).thenReturn(Optional.empty());

        executor.cancelOrder(orderId);

        verify(productRepository, never()).save(product1);
        verify(productRepository).save(product2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
    }

    // ==================== bỏ qua đơn không còn đủ điều kiện ====================

    @Test
    @DisplayName("cancelOrder: đơn không còn tồn tại -> bỏ qua")
    void cancelOrder_orderNotFound_doesNothing() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        executor.cancelOrder(orderId);

        verify(orderItemRepository, never()).findAllByOrder_Id(any());
        verify(productRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("cancelOrder: đơn đã rời PENDING_PAYMENT (vd đã PAID trước khi job chạy) -> không huỷ")
    void cancelOrder_statusNotPendingPayment_doesNothing(OrderStatus status) {
        order.setStatus(status);

        executor.cancelOrder(orderId);

        verify(orderItemRepository, never()).findAllByOrder_Id(any());
        verify(productRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        assertThat(order.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("cancelOrder: hạn thanh toán chưa tới (khách vừa gia hạn / job chạy sớm) -> không huỷ")
    void cancelOrder_deadlineNotReached_doesNothing() {
        order.setPaymentDeadline(Instant.now().plus(5, ChronoUnit.MINUTES));

        executor.cancelOrder(orderId);

        verify(orderItemRepository, never()).findAllByOrder_Id(any());
        verify(productRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getCancelledAt()).isNull();
    }
}