package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.NotificationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.chat.Notification;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.repository.FollowRepository;
import com.rehome.rehome.repository.NotificationRepository;
import com.rehome.rehome.service.NotificationService;
import com.rehome.rehome.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private static final String NOTIFICATION_QUEUE = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final FollowRepository followRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationService pushNotificationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listNotifications(UUID accountId, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Notification> result = notificationRepository.findByRecipientAccount_Id(accountId, pageable);
        return PageResponse.from(result.map(NotificationServiceImpl::toResponse));
    }

    @Override
    @Transactional
    public void notify(Account recipientAccount, NotificationType type, String title, String body,
                        NotificationTargetType targetType, UUID targetId, UUID warehouseId) {
        Notification notification = Notification.builder()
                .recipientAccount(recipientAccount)
                .type(type)
                .title(title)
                .body(body)
                .targetType(targetType)
                .targetId(targetId)
                .warehouseId(warehouseId)
                .createdAt(Instant.now())
                .build();
        notificationRepository.save(notification);
        messagingTemplate.convertAndSendToUser(
                recipientAccount.getEmail(), NOTIFICATION_QUEUE, toResponse(notification));
        // WebSocket chỉ tới được nếu app đang mở kết nối; push FCM thêm đường tới khi app đã
        // tắt/background. NoopPushNotificationServiceImpl không làm gì nếu chưa cấu hình Firebase.
        pushNotificationService.send(recipientAccount, notification);
    }

    @Override
    @Async
    @Transactional
    public void notifyFollowers(UUID organizationId, NotificationType type, String title, String body,
                                 NotificationTargetType targetType, UUID targetId) {
        List<Account> followers = followRepository.findFollowerAccounts(organizationId);
        if (followers.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        List<Notification> notifications = followers.stream()
                .map(follower -> Notification.builder()
                        .recipientAccount(follower)
                        .type(type)
                        .title(title)
                        .body(body)
                        .targetType(targetType)
                        .targetId(targetId)
                        .createdAt(now)
                        .build())
                .toList();
        notificationRepository.saveAll(notifications);
        notifications.forEach(notification -> {
            messagingTemplate.convertAndSendToUser(
                    notification.getRecipientAccount().getEmail(), NOTIFICATION_QUEUE, toResponse(notification));
            pushNotificationService.send(notification.getRecipientAccount(), notification);
        });
        log.info("Đã báo {} cho {} người theo dõi tổ chức {}", type, notifications.size(), organizationId);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID accountId, UUID notificationId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientAccount_Id(notificationId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo."));

        notification.setIsRead(true);
        return toResponse(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID accountId) {
        notificationRepository.markAllAsRead(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID accountId) {
        return notificationRepository.countByRecipientAccount_IdAndIsReadFalse(accountId);
    }

    private static NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId().toString())
                .type(notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getBody())
                .isRead(Boolean.TRUE.equals(notification.getIsRead()))
                .targetType(notification.getTargetType() == null ? null : notification.getTargetType().name())
                .targetId(notification.getTargetId() == null ? null : notification.getTargetId().toString())
                .warehouseId(notification.getWarehouseId() == null ? null : notification.getWarehouseId().toString())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
