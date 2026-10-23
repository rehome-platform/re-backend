package com.rehome.rehome.entity.enums;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PACKING,
    SHIPPING,
    DELIVERED,
    COMPLETED,
    // Hàng trả hợp lệ (hoặc thất lạc chiều trả) - đã hoàn tiền người mua, không payout người bán - F-132/F-136
    RETURNED,
    CANCELLED
}
