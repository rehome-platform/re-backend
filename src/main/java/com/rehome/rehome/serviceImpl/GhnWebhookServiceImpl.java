package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.GhnWebhookPayload;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderTrackingEvent;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.OrderTrackingEventRepository;
import com.rehome.rehome.service.GhnWebhookService;
import com.rehome.rehome.service.OrderService;
import com.rehome.rehome.service.ReturnRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

// API 71 (F-128) - đồng bộ trạng thái GHN vào Order + lịch sử OrderTrackingEvent, và tự hủy đơn khi vận chuyển lỗi (F-062)
@Service
@RequiredArgsConstructor
@Slf4j
public class GhnWebhookServiceImpl implements GhnWebhookService {

    // Các trạng thái "kết thúc" của GHN mà không phải giao thành công -> coi là vận chuyển lỗi, tự hủy đơn + hoàn tiền
    private static final Set<String> TERMINAL_FAILURE_STATUSES = Set.of(
            "cancel", "exception", "lost", "damage", "scrap", "returned");

    private final OrderRepository orderRepository;
    private final OrderTrackingEventRepository orderTrackingEventRepository;
    private final OrderService orderService;
    private final ReturnRequestService returnRequestService;

    @Value("${app.order.auto-complete-days:3}")
    private long autoCompleteDays;

    @Override
    @Transactional
    public void handleWebhook(GhnWebhookPayload payload) {

        if (!StringUtils.hasText(payload.getOrderCode())) {
            throw new BadRequestException("Thiếu OrderCode trong webhook.");
        }

        Order order = orderRepository.findByGhnTrackingCode(payload.getOrderCode()).orElse(null);

        // Không khớp vận đơn chiều giao thì thử vận đơn chiều trả hàng (F-068/F-136)
        if (order == null) {
            boolean handledAsReturn = returnRequestService.applyGhnReturnStatus(
                    payload.getOrderCode(), payload.getStatus(), payload.getReason());

            if (!handledAsReturn) {
                throw new IllegalArgumentException("Không tìm thấy vận đơn.");
            }
            return;
        }

        Instant occurredAt = parseTime(payload.getTime());

        orderTrackingEventRepository.save(OrderTrackingEvent.builder()
                .order(order)
                .ghnStatus(payload.getStatus())
                .description(StringUtils.hasText(payload.getReason()) ? payload.getReason() : payload.getDescription())
                .occurredAt(occurredAt)
                .build());

        String status = payload.getStatus() == null ? "" : payload.getStatus().toLowerCase(Locale.ROOT);

        if ("delivered".equals(status)) {
            if (order.getStatus() == OrderStatus.SHIPPING) {
                order.setStatus(OrderStatus.DELIVERED);
                order.setDeliveredAt(occurredAt);
                order.setAutoCompleteDeadline(occurredAt.plus(autoCompleteDays, ChronoUnit.DAYS));
                orderRepository.save(order);
            }
        } else if (TERMINAL_FAILURE_STATUSES.contains(status)) {
            String reason = StringUtils.hasText(payload.getReason()) ? payload.getReason() : status;
            orderService.cancelOrderForCarrierFailure(order.getId(), reason);
        }
        // Các trạng thái trung gian (ready_to_pick, picking, storing, transporting, delivering, ...)
        // chỉ cần ghi vào OrderTrackingEvent phía trên, không đổi Order.status.
    }

    private Instant parseTime(String time) {
        if (!StringUtils.hasText(time)) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(time).toInstant();
        } catch (DateTimeParseException ex) {
            return Instant.now();
        }
    }
}
