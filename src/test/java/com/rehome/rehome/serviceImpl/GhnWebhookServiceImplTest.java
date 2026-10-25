package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.GhnWebhookPayload;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderTrackingEvent;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.OrderTrackingEventRepository;
import com.rehome.rehome.service.OrderService;
import com.rehome.rehome.service.ReturnRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// API 71 (F-128) - đồng bộ trạng thái GHN, và F-062 - tự hủy đơn khi vận chuyển lỗi
@ExtendWith(MockitoExtension.class)
class GhnWebhookServiceImplTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderTrackingEventRepository orderTrackingEventRepository;
    @Mock OrderService orderService;
    @Mock ReturnRequestService returnRequestService;

    @InjectMocks GhnWebhookServiceImpl ghnWebhookService;

    static final String TRACKING_CODE = "GHN0123456789";

    UUID orderId;
    Order order;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(ghnWebhookService, "autoCompleteDays", 3L);

        orderId = UUID.randomUUID();
        order = Order.builder()
                .id(orderId)
                .orderNo("OD-2026-000777")
                .status(OrderStatus.SHIPPING)
                .ghnTrackingCode(TRACKING_CODE)
                .build();
    }

    private GhnWebhookPayload payload(String status, String reason, String time) {
        GhnWebhookPayload p = new GhnWebhookPayload();
        p.setOrderCode(TRACKING_CODE);
        p.setStatus(status);
        p.setReason(reason);
        p.setTime(time);
        return p;
    }

    private OrderTrackingEvent capturedEvent() {
        ArgumentCaptor<OrderTrackingEvent> captor = ArgumentCaptor.forClass(OrderTrackingEvent.class);
        verify(orderTrackingEventRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- tìm vận đơn

    @Test
    void handleWebhook_throwsBadRequest_whenOrderCodeMissing() {
        GhnWebhookPayload p = new GhnWebhookPayload();
        p.setStatus("delivered");

        assertThatThrownBy(() -> ghnWebhookService.handleWebhook(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Thiếu OrderCode trong webhook.");

        verifyNoInteractions(orderRepository, orderTrackingEventRepository, orderService, returnRequestService);
    }

    @Test
    void handleWebhook_throwsNotFound_whenCodeMatchesNeitherDeliveryNorReturnShipment() {
        when(orderRepository.findByGhnTrackingCode(TRACKING_CODE)).thenReturn(Optional.empty());
        when(returnRequestService.applyGhnReturnStatus(TRACKING_CODE, "lost", "Thất lạc")).thenReturn(false);

        assertThatThrownBy(() -> ghnWebhookService.handleWebhook(payload("lost", "Thất lạc", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy vận đơn.");

        verifyNoInteractions(orderTrackingEventRepository, orderService);
    }

    @Test
    void handleWebhook_delegatesToReturnFlow_whenCodeBelongsToReturnShipment() {
        when(orderRepository.findByGhnTrackingCode(TRACKING_CODE)).thenReturn(Optional.empty());
        when(returnRequestService.applyGhnReturnStatus(TRACKING_CODE, "delivered", null)).thenReturn(true);

        ghnWebhookService.handleWebhook(payload("delivered", null, null));

        // Vận đơn chiều trả có lịch sử riêng, không ghi vào OrderTrackingEvent và không hủy đơn gốc
        verifyNoInteractions(orderTrackingEventRepository, orderService);
        verify(orderRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- lịch sử vận chuyển

    @Test
    void handleWebhook_savesTrackingEvent_preferringReasonOverDescription() {
        GhnWebhookPayload p = payload("delivering", "Đang giao cho người nhận", "2026-03-01T10:15:00Z");
        p.setDescription("mô tả dự phòng");
        when(orderRepository.findByGhnTrackingCode(TRACKING_CODE)).thenReturn(Optional.of(order));

        ghnWebhookService.handleWebhook(p);

        OrderTrackingEvent event = capturedEvent();
        assertThat(event.getOrder()).isEqualTo(order);
        assertThat(event.getGhnStatus()).isEqualTo("delivering");
        assertThat(event.getDescription()).isEqualTo("Đang giao cho người nhận");
        assertThat(event.getOccurredAt()).isEqualTo(Instant.parse("2026-03-01T10:15:00Z"));
    }

    @Test
    void handleWebhook_savesTrackingEvent_fallingBackToDescription_whenReasonBlank() {
        GhnWebhookPayload p = payload("storing", null, "2026-03-01T10:15:00Z");
        p.setDescription("Đang lưu kho GHN");
        when(orderRepository.findByGhnTrackingCode(TRACKING_CODE)).thenReturn(Optional.of(order));

        ghnWebhookService.handleWebhook(p);

        assertThat(capturedEvent().getDescription()).isEqualTo("Đang lưu kho GHN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "không-phải-định-dạng-thời-gian"})
    void handleWebhook_fallsBackToNow_whenTimeIsMissingOrUnparseable(String time) {
        when(orderRepository.findByGhnTrackingCode(TRACKING_CODE)).thenReturn(Optional.of(order));
        Instant beforeCall = Instant.now();

        ghnWebhookService.handleWebhook(payload("transporting", null, time));

        assertThat(capturedEvent().getOccurredAt()).isAfterOrEqualTo(beforeCall);
    }

    @Test
    void handleWebhook_onlyRecordsHistory_forIntermediateStatus() {
        when(orderRepository.findByGhnTrackingCode(TRACKING_CODE)).thenReturn(Optional.of(order));

        ghnWebhookService.handleWebhook(payload("transporting", null, "2026-03-01T10:15:00Z"));

        verify(orderTrackingEventRepository).save(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderService);
    }

    // ---------------------------------------------------------------- giao thành công (F-064)

    @Test
    void handleWebhook_marksDeliveredAndSetsAutoCompleteDeadline_whenOrderIsShipping() {
        when(orderRepository.findByGhnTrackingCode(TRACKING_CODE)).thenReturn(Optional.of(order));

        ghnWebhookService.handleWebhook(payload("delivered", null, "2026-03-01T10:15:00Z"));

        Instant occurredAt = Instant.parse("2026-03-01T10:15:00Z");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveredAt()).isEqualTo(occurredAt);
        assertThat(order.getAutoCompleteDeadline()).isEqualTo(occurredAt.plus(3, ChronoUnit.DAYS));
        verify(orderRepository).save(order);
        verifyNoInteractions(orderService);
    }

    @Test
    void handleWebhook_ignoresDuplicateDelivered_whenOrderAlreadyDelivered() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findByGhnTrackingCode(TRACKING_CODE)).thenReturn(Optional.of(order));

        ghnWebhookService.handleWebhook(payload("delivered", null, "2026-03-02T10:15:00Z"));

        verify(orderTrackingEventRepository).save(any());
        assertThat(order.getDeliveredAt()).isNull();
        assertThat(order.getAutoCompleteDeadline()).isNull();
        verify(orderRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- F-062 - vận chuyển lỗi

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "exception", "lost", "damage", "scrap", "returned"})
    void handleWebhook_cancelsOrder_forEveryTerminalFailureStatus(String status) {
        when(orderRepository.findByGhnTrackingCode(TRACKING_CODE)).thenReturn(Optional.of(order));

        ghnWebhookService.handleWebhook(payload(status, "GHN báo lỗi vận chuyển", null));

        verify(orderService).cancelOrderForCarrierFailure(orderId, "GHN báo lỗi vận chuyển");
        // Việc đổi trạng thái đơn do OrderService lo, webhook không tự ghi đè
        verify(orderRepository, never()).save(any());
    }

    @Test
    void handleWebhook_cancelsOrder_whenGhnSendsStatusInUpperCase() {
        when(orderRepository.findByGhnTrackingCode(TRACKING_CODE)).thenReturn(Optional.of(order));

        ghnWebhookService.handleWebhook(payload("LOST", "Thất lạc", null));

        verify(orderService).cancelOrderForCarrierFailure(orderId, "Thất lạc");
    }

    @Test
    void handleWebhook_passesStatusAsReason_whenGhnSendsNoReason() {
        when(orderRepository.findByGhnTrackingCode(TRACKING_CODE)).thenReturn(Optional.of(order));

        ghnWebhookService.handleWebhook(payload("damage", null, null));

        verify(orderService).cancelOrderForCarrierFailure(orderId, "damage");
    }

    @Test
    void handleWebhook_stillRecordsHistory_whenCarrierFailureCancelsOrder() {
        when(orderRepository.findByGhnTrackingCode(TRACKING_CODE)).thenReturn(Optional.of(order));

        ghnWebhookService.handleWebhook(payload("lost", "Thất lạc kiện hàng", "2026-03-01T10:15:00Z"));

        OrderTrackingEvent event = capturedEvent();
        assertThat(event.getGhnStatus()).isEqualTo("lost");
        assertThat(event.getDescription()).isEqualTo("Thất lạc kiện hàng");
        assertThat(event.getOccurredAt()).isEqualTo(Instant.parse("2026-03-01T10:15:00Z"));
    }
}
