package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.NotificationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.chat.Notification;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.repository.NotificationRepository;
import com.rehome.rehome.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;

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
    }

    private static NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId().toString())
                .type(notification.getType().name())
                .message(notification.getBody())
                .isRead(Boolean.TRUE.equals(notification.getIsRead()))
                .targetType(notification.getTargetType() == null ? null : notification.getTargetType().name())
                .targetId(notification.getTargetId() == null ? null : notification.getTargetId().toString())
                .warehouseId(notification.getWarehouseId() == null ? null : notification.getWarehouseId().toString())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
