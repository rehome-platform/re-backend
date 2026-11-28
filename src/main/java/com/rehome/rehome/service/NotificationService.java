package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.NotificationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.NotificationType;

import java.util.UUID;

public interface NotificationService {

    PageResponse<NotificationResponse> listNotifications(UUID accountId, int page, int size);

    void notifyProduct(Account recipientAccount, NotificationType type, String title, String body,
                       String productId, UUID warehouseId);

    void notify(Account recipientAccount, NotificationType type, String title, String body,
                NotificationTargetType targetType, UUID targetId, UUID warehouseId);

    void notifyFollowers(UUID organizationId, NotificationType type, String title, String body,
                          NotificationTargetType targetType, UUID targetId);

    NotificationResponse markAsRead(UUID accountId, UUID notificationId);

    void markAllAsRead(UUID accountId);

    long countUnread(UUID accountId);
}
