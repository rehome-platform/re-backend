package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.GhnWebhookPayload;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderTrackingEvent;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.OrderTrackingEventRepository;
import com.rehome.rehome.service.OrderService;
import com.rehome.rehome.service.ReturnRequestService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GhnWebhookServiceImplTest {

    private static final String ORDER_CODE = "GHN123";
    private static final String TIME = "2026-10-20T10:00:00+07:00";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderTrackingEventRepository orderTrackingEventRepository;
    @Mock
    private OrderService orderService;
    @Mock
    private ReturnRequestService returnRequestService;

    @InjectMocks
    private GhnWebhookServiceImpl service;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "autoCompleteDays", 3L);
        orderId = UUID.randomUUID();
    }

    // ---------- helpers ----------

    private GhnWebhookPayload payload(String orderCode, String status, String reason, String time) {
        GhnWebhookPayload p = mock(GhnWebhookPayload.class);
        when(p.getOrderCode()).thenReturn(orderCode);
        when(p.getStatus()).thenReturn(status);
        when(p.getReason()).thenReturn(reason);
        when(p.getTime()).thenReturn(time);
        when(p.getDescription()).thenReturn("mô tả GHN");
        when(p.getWarehouse()).thenReturn("Kho A");
        return p;
    }

    private Order order(OrderStatus status) {
        Order o = mock(Order.class);
        when(o.getId()).thenReturn(orderId);
        when(o.getStatus()).thenReturn(status);
        return o;
    }

    private void assertAppException(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private OrderTrackingEvent captureSavedEvent() {
        ArgumentCaptor<OrderTrackingEvent> captor = ArgumentCaptor.forClass(OrderTrackingEvent.class);
        verify(orderTrackingEventRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------- validation ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void handleWebhook_throwsBadRequest_whenOrderCodeMissing(String code) {
        GhnWebhookPayload p = payload(code, "delivered", null, TIME);

        assertAppException(() -> service.handleWebhook(p), ErrorCode.GHN_ORDER_CODE_REQUIRED);

        verifyNoInteractions(orderRepository, orderTrackingEventRepository, orderService, returnRequestService);
    }

    // ---------- không khớp vận đơn chiều giao ----------

    @Test
    void handleWebhook_throwsNotFound_whenCodeMatchesNeitherDeliveryNorReturnShipment() {
        GhnWebhookPayload p = payload(ORDER_CODE, "delivered", "lý do", TIME);
        when(orderRepository.findByGhnTrackingCode(ORDER_CODE)).thenReturn(Optional.empty());
        when(returnRequestService.applyGhnReturnStatus(ORDER_CODE, "delivered", "lý do")).thenReturn(false);

        assertAppException(() -> service.handleWebhook(p), ErrorCode.GHN_SHIPMENT_NOT_FOUND);

        verify(orderTrackingEventRepository, never()).save(any());
        verifyNoInteractions(orderService);
    }

    @Test
    void handleWebhook_handledAsReturnShipment_doesNotTouchOrderFlow() {
        GhnWebhookPayload p = payload(ORDER_CODE, "returned", "khách trả", TIME);
        when(orderRepository.findByGhnTrackingCode(ORDER_CODE)).thenReturn(Optional.empty());
        when(returnRequestService.applyGhnReturnStatus(ORDER_CODE, "returned", "khách trả")).thenReturn(true);

        service.handleWebhook(p);

        verify(returnRequestService).applyGhnReturnStatus(ORDER_CODE, "returned", "khách trả");
        verify(orderTrackingEventRepository, never()).save(any());
        verifyNoInteractions(orderService);
    }

    // ---------- delivered ----------

    @Test
    void handleWebhook_delivered_whenShipping_marksOrderDeliveredAndSetsDeadline() {
        Order order = order(OrderStatus.SHIPPING);
        GhnWebhookPayload p = payload(ORDER_CODE, "delivered", null, TIME);
        when(orderRepository.findByGhnTrackingCode(ORDER_CODE)).thenReturn(Optional.of(order));
        Instant expected = OffsetDateTime.parse(TIME).toInstant();

        service.handleWebhook(p);

        verify(order).setStatus(OrderStatus.DELIVERED);
        verify(order).setDeliveredAt(expected);
        verify(order).setAutoCompleteDeadline(expected.plus(3, ChronoUnit.DAYS));
        verify(orderRepository).save(order);
        verify(orderService, never()).cancelOrderForCarrierFailure(any(), any());
    }

    @Test
    void handleWebhook_delivered_whenNotShipping_doesNotChangeOrder() {
        Order order = order(OrderStatus.DELIVERED);
        GhnWebhookPayload p = payload(ORDER_CODE, "delivered", null, TIME);
        when(orderRepository.findByGhnTrackingCode(ORDER_CODE)).thenReturn(Optional.of(order));

        service.handleWebhook(p);

        verify(order, never()).setStatus(any());
        verify(orderRepository, never()).save(any());
        // vẫn ghi lịch sử tracking
        verify(orderTrackingEventRepository).save(any(OrderTrackingEvent.class));
    }

    // ---------- trạng thái lỗi vận chuyển -> tự hủy đơn ----------

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "exception", "lost", "damage", "scrap", "returned"})
    void handleWebhook_terminalFailureStatus_cancelsOrderWithReason(String status) {
        Order order = order(OrderStatus.SHIPPING);
        GhnWebhookPayload p = payload(ORDER_CODE, status, "hàng thất lạc", TIME);
        when(orderRepository.findByGhnTrackingCode(ORDER_CODE)).thenReturn(Optional.of(order));

        service.handleWebhook(p);

        verify(orderService).cancelOrderForCarrierFailure(orderId, "hàng thất lạc");
        verify(order, never()).setStatus(OrderStatus.DELIVERED);
    }

    @Test
    void handleWebhook_terminalFailureStatus_usesStatusAsReason_whenReasonBlank() {
        Order order = order(OrderStatus.SHIPPING);
        GhnWebhookPayload p = payload(ORDER_CODE, "LOST", "  ", TIME);
        when(orderRepository.findByGhnTrackingCode(ORDER_CODE)).thenReturn(Optional.of(order));

        service.handleWebhook(p);

        verify(orderService).cancelOrderForCarrierFailure(orderId, "lost");
    }

    // ---------- trạng thái trung gian ----------

    @ParameterizedTest
    @ValueSource(strings = {"ready_to_pick", "picking", "storing", "transporting", "delivering"})
    void handleWebhook_intermediateStatus_onlyRecordsTrackingEvent(String status) {
        Order order = order(OrderStatus.SHIPPING);
        GhnWebhookPayload p = payload(ORDER_CODE, status, null, TIME);
        when(orderRepository.findByGhnTrackingCode(ORDER_CODE)).thenReturn(Optional.of(order));

        service.handleWebhook(p);

        verify(orderTrackingEventRepository).save(any(OrderTrackingEvent.class));
        verify(order, never()).setStatus(any());
        verify(orderRepository, never()).save(any());
        verify(orderService, never()).cancelOrderForCarrierFailure(any(), any());
    }

    // ---------- tracking event ----------

    @Test
    void handleWebhook_trackingEvent_usesReasonWhenPresent() {
        Order order = order(OrderStatus.SHIPPING);
        GhnWebhookPayload p = payload(ORDER_CODE, "picking", "đang lấy hàng", TIME);
        when(orderRepository.findByGhnTrackingCode(ORDER_CODE)).thenReturn(Optional.of(order));

        service.handleWebhook(p);

        OrderTrackingEvent event = captureSavedEvent();
        assertThat(event.getGhnStatus()).isEqualTo("picking");
        assertThat(event.getDescription()).isEqualTo("đang lấy hàng");
        assertThat(event.getLocation()).isEqualTo("Kho A");
        assertThat(event.getOccurredAt()).isEqualTo(OffsetDateTime.parse(TIME).toInstant());
    }

    @Test
    void handleWebhook_trackingEvent_fallsBackToDescription_whenReasonBlank() {
        Order order = order(OrderStatus.SHIPPING);
        GhnWebhookPayload p = payload(ORDER_CODE, "picking", "", TIME);
        when(orderRepository.findByGhnTrackingCode(ORDER_CODE)).thenReturn(Optional.of(order));

        service.handleWebhook(p);

        assertThat(captureSavedEvent().getDescription()).isEqualTo("mô tả GHN");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-date"})
    void handleWebhook_invalidOrMissingTime_fallsBackToNow(String time) {
        Order order = order(OrderStatus.SHIPPING);
        GhnWebhookPayload p = payload(ORDER_CODE, "picking", null, time);
        when(orderRepository.findByGhnTrackingCode(ORDER_CODE)).thenReturn(Optional.of(order));
        Instant before = Instant.now();

        service.handleWebhook(p);

        Instant occurredAt = captureSavedEvent().getOccurredAt();
        assertThat(occurredAt).isBetween(before, Instant.now());
    }

    @Test
    void handleWebhook_nullStatus_doesNotThrowAndOnlyRecordsEvent() {
        Order order = order(OrderStatus.SHIPPING);
        GhnWebhookPayload p = payload(ORDER_CODE, null, null, TIME);
        when(orderRepository.findByGhnTrackingCode(ORDER_CODE)).thenReturn(Optional.of(order));

        service.handleWebhook(p);

        assertThat(captureSavedEvent().getGhnStatus()).isNull();
        verify(order, never()).setStatus(any());
        verify(orderService, never()).cancelOrderForCarrierFailure(any(), any());
    }
}