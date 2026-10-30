package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Logic nhả hàng / huỷ đơn đã chuyển sang OrderCancelExecutor (xem OrderCancelExecutorTest).
// Class này chỉ còn trách nhiệm: tìm đơn quá hạn và gọi executor cho từng đơn.
@ExtendWith(MockitoExtension.class)
class OrderAutoCancelServiceImplTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderCancelExecutor orderCancelExecutor;

    @InjectMocks
    OrderAutoCancelServiceImpl orderAutoCancelService;

    Order order1;
    Order order2;
    Order order3;

    @BeforeEach
    void setup() {
        order1 = Order.builder().id(UUID.randomUUID()).orderNo("OD-2026-000001")
                .status(OrderStatus.PENDING_PAYMENT).build();
        order2 = Order.builder().id(UUID.randomUUID()).orderNo("OD-2026-000002")
                .status(OrderStatus.PENDING_PAYMENT).build();
        order3 = Order.builder().id(UUID.randomUUID()).orderNo("OD-2026-000003")
                .status(OrderStatus.PENDING_PAYMENT).build();
    }

    @Test
    @DisplayName("cancelExpiredOrders: không có đơn quá hạn -> không gọi executor")
    void cancelExpiredOrders_noExpiredOrders_doesNothing() {
        when(orderRepository.findAllByStatusAndPaymentDeadlineBefore(eq(OrderStatus.PENDING_PAYMENT), any(Instant.class)))
                .thenReturn(List.of());

        orderAutoCancelService.cancelExpiredOrders();

        verifyNoInteractions(orderCancelExecutor);
    }

    @Test
    @DisplayName("cancelExpiredOrders: gọi executor.cancelOrder cho từng đơn quá hạn theo thứ tự")
    void cancelExpiredOrders_callsExecutorForEachExpiredOrder() {
        when(orderRepository.findAllByStatusAndPaymentDeadlineBefore(eq(OrderStatus.PENDING_PAYMENT), any(Instant.class)))
                .thenReturn(List.of(order1, order2, order3));

        orderAutoCancelService.cancelExpiredOrders();

        InOrder inOrder = inOrder(orderCancelExecutor);
        inOrder.verify(orderCancelExecutor).cancelOrder(order1.getId());
        inOrder.verify(orderCancelExecutor).cancelOrder(order2.getId());
        inOrder.verify(orderCancelExecutor).cancelOrder(order3.getId());
    }

    @Test
    @DisplayName("cancelExpiredOrders: một đơn lỗi -> không ném ra ngoài, các đơn còn lại vẫn được xử lý")
    void cancelExpiredOrders_oneOrderFails_continuesWithOthers() {
        when(orderRepository.findAllByStatusAndPaymentDeadlineBefore(eq(OrderStatus.PENDING_PAYMENT), any(Instant.class)))
                .thenReturn(List.of(order1, order2, order3));
        doThrow(new RuntimeException("lock timeout")).when(orderCancelExecutor).cancelOrder(order2.getId());

        assertThatCode(() -> orderAutoCancelService.cancelExpiredOrders()).doesNotThrowAnyException();

        verify(orderCancelExecutor).cancelOrder(order1.getId());
        verify(orderCancelExecutor).cancelOrder(order2.getId());
        verify(orderCancelExecutor).cancelOrder(order3.getId());
    }

    @Test
    @DisplayName("cancelExpiredOrders: service không tự lưu đơn, việc ghi DB nằm hết trong executor")
    void cancelExpiredOrders_doesNotWriteOrdersItself() {
        when(orderRepository.findAllByStatusAndPaymentDeadlineBefore(eq(OrderStatus.PENDING_PAYMENT), any(Instant.class)))
                .thenReturn(List.of(order1));

        orderAutoCancelService.cancelExpiredOrders();

        verify(orderRepository, never()).save(any());
        verify(orderRepository, never()).findById(any());
    }
}
