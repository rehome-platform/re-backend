package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.repository.ReturnRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnRequestAutoExpireServiceImplTest {

    @Mock
    ReturnRequestRepository returnRequestRepository;

    @Mock
    QueueService queueService;

    @InjectMocks
    ReturnRequestAutoExpireServiceImpl service;

    ReturnRequest approved1;
    ReturnRequest approved2;
    ReturnRequest invalid1;
    ReturnRequest invalid2;

    @BeforeEach
    void setup() {
        approved1 = ReturnRequest.builder().id(UUID.randomUUID())
                .status(ReturnRequestStatus.APPROVED)
                .handoverDeadline(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        approved2 = ReturnRequest.builder().id(UUID.randomUUID())
                .status(ReturnRequestStatus.APPROVED)
                .handoverDeadline(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();

        invalid1 = ReturnRequest.builder().id(UUID.randomUUID())
                .status(ReturnRequestStatus.INVALID)
                .build();
        invalid1.setPickupDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        invalid2 = ReturnRequest.builder().id(UUID.randomUUID())
                .status(ReturnRequestStatus.INVALID)
                .build();
        invalid2.setPickupDeadline(Instant.now().minus(3, ChronoUnit.DAYS));
    }

    // ==================== cancelNotHandedOverRequests (F-069) ====================

    @Test
    @DisplayName("cancelNotHandedOverRequests: không có yêu cầu quá hạn -> không lưu gì")
    void cancelNotHandedOver_noneExpired_doesNothing() {
        when(returnRequestRepository.findAllByStatusAndHandoverDeadlineBefore(
                eq(ReturnRequestStatus.APPROVED), any(Instant.class))).thenReturn(List.of());

        service.cancelNotHandedOverRequests();

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelNotHandedOverRequests: quá hạn bàn giao -> CANCELLED, ghi chú, resolvedAt, lưu từng yêu cầu")
    void cancelNotHandedOver_expired_areCancelled() {
        when(returnRequestRepository.findAllByStatusAndHandoverDeadlineBefore(
                eq(ReturnRequestStatus.APPROVED), any(Instant.class)))
                .thenReturn(List.of(approved1, approved2));

        Instant before = Instant.now();
        service.cancelNotHandedOverRequests();

        ArgumentCaptor<ReturnRequest> captor = ArgumentCaptor.forClass(ReturnRequest.class);
        verify(returnRequestRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyInAnyOrder(approved1, approved2);

        for (ReturnRequest r : List.of(approved1, approved2)) {
            assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.CANCELLED);
            assertThat(r.getResolutionNote()).isEqualTo("Quá hạn bàn giao hàng trả cho GHN, yêu cầu bị hủy tự động.");
            assertThat(r.getResolvedAt()).isNotNull().isAfterOrEqualTo(before);
        }
        verify(queueService).dequeue(QueueType.RETURN, approved1.getId().toString());
        verify(queueService).dequeue(QueueType.RETURN, approved2.getId().toString());
    }

    @Test
    @DisplayName("cancelNotHandedOverRequests: chỉ truy vấn trạng thái APPROVED với mốc là thời điểm hiện tại")
    void cancelNotHandedOver_queriesApprovedBeforeNow() {
        when(returnRequestRepository.findAllByStatusAndHandoverDeadlineBefore(any(), any()))
                .thenReturn(List.of());

        Instant before = Instant.now();
        service.cancelNotHandedOverRequests();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(returnRequestRepository).findAllByStatusAndHandoverDeadlineBefore(
                eq(ReturnRequestStatus.APPROVED), instantCaptor.capture());
        assertThat(instantCaptor.getValue()).isBetween(before, after);
    }

    // ==================== expireUncollectedInvalidReturns (F-071) ====================

    @Test
    @DisplayName("expireUncollectedInvalidReturns: không có yêu cầu quá hạn -> không lưu gì")
    void expireUncollected_noneExpired_doesNothing() {
        when(returnRequestRepository.findAllByStatusAndPickupDeadlineBeforeAndResolvedAtIsNull(
                eq(ReturnRequestStatus.INVALID), any(Instant.class))).thenReturn(List.of());

        service.expireUncollectedInvalidReturns();

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("expireUncollectedInvalidReturns: quá hạn nhận lại hàng -> đóng bằng resolvedAt + ghi chú, GIỮ nguyên status INVALID")
    void expireUncollected_expired_areClosedButStayInvalid() {
        when(returnRequestRepository.findAllByStatusAndPickupDeadlineBeforeAndResolvedAtIsNull(
                eq(ReturnRequestStatus.INVALID), any(Instant.class)))
                .thenReturn(List.of(invalid1, invalid2));

        Instant before = Instant.now();
        service.expireUncollectedInvalidReturns();

        ArgumentCaptor<ReturnRequest> captor = ArgumentCaptor.forClass(ReturnRequest.class);
        verify(returnRequestRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyInAnyOrder(invalid1, invalid2);

        for (ReturnRequest r : List.of(invalid1, invalid2)) {
            assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.INVALID);
            assertThat(r.getResolutionNote()).isEqualTo("Quá hạn đến kho nhận lại hàng trả không hợp lệ.");
            assertThat(r.getResolvedAt()).isNotNull().isAfterOrEqualTo(before);
        }
        verify(queueService, never()).dequeue(any(), any());
    }

    @Test
    @DisplayName("hai job độc lập: job này không đụng tới danh sách của job kia")
    void jobs_areIndependent() {
        when(returnRequestRepository.findAllByStatusAndHandoverDeadlineBefore(any(), any()))
                .thenReturn(List.of());

        service.cancelNotHandedOverRequests();

        verify(returnRequestRepository, never())
                .findAllByStatusAndPickupDeadlineBeforeAndResolvedAtIsNull(any(), any());
    }
}
