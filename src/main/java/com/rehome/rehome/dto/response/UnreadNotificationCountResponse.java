package com.rehome.rehome.dto.response;

/**
 * GET /notifications/unread-count - dùng hiện badge số thông báo chưa đọc.
 */
public record UnreadNotificationCountResponse(long count) {
}
