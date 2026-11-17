package com.rehome.rehome.entity.enums;

public enum DonationStatus {
    // Đã ghi nhận tại điểm ký gửi, chờ tổ chức đến lấy (F-163)
    PENDING_PICKUP,
    ON_HOLD,

    // Đã bàn giao cho tổ chức đến nhận (F-165)
    HANDED_OVER
}
