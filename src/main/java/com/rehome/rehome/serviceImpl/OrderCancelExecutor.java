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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Tách riêng để @Transactional đi qua Spring proxy (gọi nội bộ cùng class thì không có tác dụng)
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancelExecutor {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);

        if (order == null
                || order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPaymentDeadline().isAfter(Instant.now())) {
            return;
        }

        List<OrderItem> items = orderItemRepository.findAllByOrder_Id(orderId);
        for (OrderItem item : items) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId()).orElse(null);
            if (product != null && product.getStatus() == ProductStatus.RESERVED) {
                product.setStatus(ProductStatus.ON_SALE);
                productRepository.save(product);
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(CancelReason.PAYMENT_EXPIRED);
        order.setCancelledAt(Instant.now());
        orderRepository.save(order);

        log.info(">>>> Tu huy don hang qua han thanh toan: orderNo={}", order.getOrderNo());
    }
}