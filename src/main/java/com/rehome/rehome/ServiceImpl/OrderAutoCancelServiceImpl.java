package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.OrderAutoCancelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// F-058: tự hủy đơn PENDING_PAYMENT quá hạn thanh toán và nhả hàng về sàn
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderAutoCancelServiceImpl implements OrderAutoCancelService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    @Override
    @Scheduled(fixedRateString = "${app.order.auto-cancel-check-interval-ms}")
    public void cancelExpiredOrders() {
        List<Order> expiredOrders = orderRepository.findAllByStatusAndPaymentDeadlineBefore(
                OrderStatus.PENDING_PAYMENT, Instant.now());

        for (Order order : expiredOrders) {
            cancelOrder(order.getId());
        }
    }

    @Transactional
    void cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);

        // Có thể đơn đã được thanh toán/xử lý ở nơi khác giữa lúc quét và lúc hủy
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
        orderRepository.save(order);

        log.info(">>>> Tu huy don hang qua han thanh toan: orderNo={}", order.getOrderNo());
    }
}
