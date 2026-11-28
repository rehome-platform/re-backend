package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.service.OrderAutoCancelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderAutoCancelServiceImpl implements OrderAutoCancelService {

    private final OrderRepository orderRepository;
    private final OrderCancelExecutor orderCancelExecutor;

    @Override
    @Scheduled(fixedRateString = "${app.order.auto-cancel-check-interval-ms}")
    public void cancelExpiredOrders() {
        List<Order> expiredOrders = orderRepository.findAllByStatusAndPaymentDeadlineBefore(
                OrderStatus.PENDING_PAYMENT, Instant.now());

        for (Order order : expiredOrders) {
            try {
                orderCancelExecutor.cancelOrder(order.getId());
            } catch (Exception e) {
                log.error(">>>> Loi khi tu huy don orderId={}", order.getId(), e);
            }
        }
    }
}
