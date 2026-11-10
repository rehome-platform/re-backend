package com.rehome.rehome.entity.enums;

// Loại màn hình mà app mở khi bấm vào thông báo, quyết định Notification.targetId trỏ bảng nào.
// Null trên Notification nghĩa là thông báo không có deep-link (chỉ hiện nội dung).
public enum NotificationTargetType {
    CAMPAIGN,
    POST,

    // Kết quả xác minh / từ chối hồ sơ tổ chức (F-138) -> mở màn hồ sơ tổ chức
    ORGANIZATION,

    // Kết quả duyệt / từ chối yêu cầu đổi thông tin pháp lý (F-140) -> mở màn yêu cầu đó
    LEGAL_CHANGE,

    // Nhắc Member chốt phương án đền bù hàng xuống cấp trước khi hết hạn (F-036) -> mở màn ca đó
    DEGRADED_ITEM_CASE
}
