package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.NotificationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.chat.Notification;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private NotificationServiceImpl service;

    private Notification notification(UUID id, NotificationType type, boolean isRead, Instant createdAt) {
        return Notification.builder()
                .id(id)
                .type(type)
                .title("Đơn hàng")
                .body("Đơn OD-2026-000123 đang được giao")
                .isRead(isRead)
                .createdAt(createdAt)
                .build();
    }

    @Test
    void listNotifications_mapsBodyToMessage() {
        UUID accountId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-13T13:10:00Z");

        when(notificationRepository.findByRecipientAccount_Id(eq(accountId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        notification(notificationId, NotificationType.ORDER_UPDATE, false, createdAt))));

        PageResponse<NotificationResponse> result = service.listNotifications(accountId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        NotificationResponse res = result.getContent().get(0);
        assertThat(res.getId()).isEqualTo(notificationId.toString());
        assertThat(res.getType()).isEqualTo("ORDER_UPDATE");
        assertThat(res.getMessage()).isEqualTo("Đơn OD-2026-000123 đang được giao");
        assertThat(res.isRead()).isFalse();
        assertThat(res.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void listNotifications_readNotification_mapsIsReadTrue() {
        UUID accountId = UUID.randomUUID();
        when(notificationRepository.findByRecipientAccount_Id(eq(accountId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification(
                        UUID.randomUUID(), NotificationType.WALLET_EVENT, true, Instant.now()))));

        PageResponse<NotificationResponse> result = service.listNotifications(accountId, 0, 20);

        assertThat(result.getContent().get(0).isRead()).isTrue();
    }

    @Test
    void listNotifications_queriesOnlyOwnNotificationsNewestFirst() {
        UUID accountId = UUID.randomUUID();
        when(notificationRepository.findByRecipientAccount_Id(eq(accountId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listNotifications(accountId, 2, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipientAccount_Id(eq(accountId), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void listNotifications_sizeClampedToMaxPageSize() {
        UUID accountId = UUID.randomUUID();
        when(notificationRepository.findByRecipientAccount_Id(eq(accountId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listNotifications(accountId, 0, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipientAccount_Id(eq(accountId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void listNotifications_negativePage_isClampedToZero() {
        UUID accountId = UUID.randomUUID();
        when(notificationRepository.findByRecipientAccount_Id(eq(accountId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listNotifications(accountId, -5, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipientAccount_Id(eq(accountId), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
    }

    @Test
    void listNotifications_empty_returnsEmptyPage() {
        UUID accountId = UUID.randomUUID();
        when(notificationRepository.findByRecipientAccount_Id(eq(accountId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<NotificationResponse> result = service.listNotifications(accountId, 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
