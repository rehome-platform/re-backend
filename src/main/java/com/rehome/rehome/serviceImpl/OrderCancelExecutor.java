package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.CancelReason;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.repository.MemberVoucherRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final MemberVoucherRepository memberVoucherRepository;

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

        // Quá hạn thanh toán: chưa thu tiền và người mua không có lỗi -> trả mã về ví (§12.4)
        releaseVoucher(order);

        log.info(">>>> Tu huy don hang qua han thanh toan: orderNo={}", order.getOrderNo());
    }

    /**
     * Trả voucher về {@code UNUSED} - <b>chỉ khi MỌI đơn mang mã đó đều đã hủy</b>. Một mã áp cho
     * cả lần thanh toán và có thể trải trên nhiều đơn: còn một đơn chưa hủy thì mã vẫn {@code USED},
     * và phần giảm của đơn bị hủy KHÔNG được chuyển sang đơn còn lại.
     *
     * <p>Mã đã hết hạn tại thời điểm trả thì đặt {@code EXPIRED} thay vì {@code UNUSED}.
     *
     * <p>Logic này trùng {@code OrderServiceImpl.releaseVoucherIfAllOrdersCancelled} nhưng cố ý
     * không gọi sang đó: executor này chạy {@code REQUIRES_NEW} trong job tự hủy, gọi vào
     * OrderService sẽ kéo cả bean đồ sộ đó vào một transaction mới và tạo phụ thuộc vòng.
     */
    private void releaseVoucher(Order cancelledOrder) {
        MemberVoucher voucher = cancelledOrder.getVoucher();
        if (voucher == null) {
            return;
        }

        cancelledOrder.setVoucher(null);
        cancelledOrder.setDiscountAmount(BigDecimal.ZERO);
        orderRepository.save(cancelledOrder);

        for (Order sibling : orderRepository.findAllByVoucher_Id(voucher.getId())) {
            if (sibling.getStatus() != OrderStatus.CANCELLED) {
                return;
            }
        }

        MemberVoucher locked = memberVoucherRepository.findByCodeForUpdate(voucher.getCode()).orElse(null);
        if (locked == null || locked.getStatus() != MemberVoucherStatus.USED) {
            return;
        }
        if (!locked.getExpiresAt().isAfter(Instant.now())) {
            locked.setStatus(MemberVoucherStatus.EXPIRED);
        } else {
            locked.setStatus(MemberVoucherStatus.UNUSED);
            locked.setUsedAt(null);
        }
        memberVoucherRepository.save(locked);
        log.info(">>>> Tra ma {} ve {} sau khi don qua han thanh toan bi huy",
                locked.getCode(), locked.getStatus());
    }
}