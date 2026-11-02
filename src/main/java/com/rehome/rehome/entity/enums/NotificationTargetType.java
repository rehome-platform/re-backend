package com.rehome.rehome.entity.enums;

// Loại màn hình mà app mở khi bấm vào thông báo, quyết định Notification.targetId trỏ bảng nào.
// Null trên Notification nghĩa là thông báo không có deep-link (chỉ hiện nội dung).
public enum NotificationTargetType {
    CAMPAIGN,
    POST
}
