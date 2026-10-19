package com.rehome.rehome.ServiceImpl;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAutoCancelServiceImplTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderItemRepository orderItemRepository;

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    OrderAutoCancelServiceImpl orderAutoCancelService;

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
                .orderNo("OD-2026-000001")
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentDeadline(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();

        product1 = Product.builder()
                .id("Q7-AK-000845")
                .status(ProductStatus.RESERVED)
                .build();

        product2 = Product.builder()
                .id("Q7-AK-000846")
                .status(ProductStatus.RESERVED)
                .build();

        item1 = OrderItem.builder().order(order).product(product1).build();
        item2 = OrderItem.builder().order(order).product(product2).build();
    }

    @Test
    void cancelExpiredOrders_cancelsOrderAndReleasesReservedProducts_whenDeadlinePassed() {
        when(orderRepository.findAllByStatusAndPaymentDeadlineBefore(eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of(order));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of(item1, item2));
        when(productRepository.findByIdForUpdate(product1.getId())).thenReturn(Optional.of(product1));
        when(productRepository.findByIdForUpdate(product2.getId())).thenReturn(Optional.of(product2));

        orderAutoCancelService.cancelExpiredOrders();

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, org.mockito.Mockito.times(2)).save(productCaptor.capture());
        assertThat(productCaptor.getAllValues())
                .allSatisfy(product -> assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderCaptor.getValue().getCancelReason()).isEqualTo(CancelReason.PAYMENT_EXPIRED);
        assertThat(orderCaptor.getValue().getCancelledAt()).isNotNull();
    }

    @Test
    void cancelExpiredOrders_doesNothing_whenNoExpiredOrders() {
        when(orderRepository.findAllByStatusAndPaymentDeadlineBefore(eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of());

        orderAutoCancelService.cancelExpiredOrders();

        verify(orderRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void cancelExpiredOrders_skipsOrder_whenAlreadyPaidBeforeJobRuns() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findAllByStatusAndPaymentDeadlineBefore(eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of(order));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderAutoCancelService.cancelExpiredOrders();

        verify(orderItemRepository, never()).findAllByOrder_Id(any());
        verify(orderRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void cancelExpiredOrders_skipsOrder_whenOrderNoLongerExists() {
        when(orderRepository.findAllByStatusAndPaymentDeadlineBefore(eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of(order));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        orderAutoCancelService.cancelExpiredOrders();

        verify(orderItemRepository, never()).findAllByOrder_Id(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelExpiredOrders_skipsOrder_whenDeadlineNotActuallyPassed() {
        order.setPaymentDeadline(Instant.now().plus(5, ChronoUnit.MINUTES));
        when(orderRepository.findAllByStatusAndPaymentDeadlineBefore(eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of(order));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderAutoCancelService.cancelExpiredOrders();

        verify(orderItemRepository, never()).findAllByOrder_Id(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelExpiredOrders_skipsProductsNotCurrentlyReserved() {
        product1.setStatus(ProductStatus.SOLD);
        when(orderRepository.findAllByStatusAndPaymentDeadlineBefore(eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of(order));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrder_Id(orderId)).thenReturn(List.of(item1, item2));
        when(productRepository.findByIdForUpdate(product1.getId())).thenReturn(Optional.of(product1));
        when(productRepository.findByIdForUpdate(product2.getId())).thenReturn(Optional.of(product2));

        orderAutoCancelService.cancelExpiredOrders();

        verify(productRepository, never()).save(product1);
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getId()).isEqualTo(product2.getId());
        assertThat(productCaptor.getValue().getStatus()).isEqualTo(ProductStatus.ON_SALE);

        verify(orderRepository).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
