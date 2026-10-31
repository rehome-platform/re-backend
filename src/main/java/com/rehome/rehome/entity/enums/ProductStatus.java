package com.rehome.rehome.entity.enums;

public enum ProductStatus {
    ON_SALE,
    RESERVED,
    SOLD,
    WITHDRAWN,
    UNCLAIMED,
    DONATED,
    DESTROYED,
    // Trả hàng hợp lệ/thất lạc (F-132/F-136) - về kho chờ Moderator quyết định đăng bán lại,
    // trả về người ký gửi hay tiêu hủy (F-133, chưa triển khai) - xem OrderServiceImpl.refundOrderForReturn
    RETURNED
}
