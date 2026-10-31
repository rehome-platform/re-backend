package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.service.OrderAutoCompleteService;
import com.rehome.rehome.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

// F-064 (tự hoàn thành đơn) gộp với job trả tiền người ký gửi sau khi hết hạn trả hàng 3 ngày.
// Tiền không được trả ngay lúc confirm-receipt nữa (xem OrderServiceImpl.confirmReceipt) - đơn
// DELIVERED/COMPLETED nào đã quá autoCompleteDeadline, chưa có payoutAt, và không còn yêu cầu
// trả hàng đang mở thì được xử lý ở đây.
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderAutoCompleteServiceImpl implements OrderAutoCompleteService {

    private static final List<ReturnRequestStatus> OPEN_RETURN_STATUSES = List.of(
            ReturnRequestStatus.PENDING,
            ReturnRequestStatus.APPROVED,
            ReturnRequestStatus.SHIPPED_BACK,
            ReturnRequestStatus.INSPECTING);

    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final OrderService orderService;

    @Override
    @Scheduled(fixedRateString = "${app.order.auto-complete-check-interval-ms}")
    public void autoCompleteAndPayout() {
        List<Order> candidates = orderRepository.findAllByStatusInAndAutoCompleteDeadlineBeforeAndPayoutAtIsNull(
                List.of(OrderStatus.DELIVERED, OrderStatus.COMPLETED), Instant.now());

        for (Order order : candidates) {
            try {
                if (returnRequestRepository.existsByOrder_IdAndStatusIn(order.getId(), OPEN_RETURN_STATUSES)) {
                    continue;
                }

                if (order.getStatus() == OrderStatus.DELIVERED) {
                    orderService.autoCompleteOrder(order.getId());
                }

                orderService.payoutOrder(order.getId(),
                        "Tự động chuyển tiền sau hạn trả hàng - đơn " + order.getOrderNo());

            } catch (Exception e) {
                log.error(">>>> Loi tu hoan thanh/tra tien don orderId={}", order.getId(), e);
            }
        }
    }
}
