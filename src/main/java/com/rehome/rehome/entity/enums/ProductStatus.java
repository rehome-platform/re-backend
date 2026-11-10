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
    RETURNED,
    // Hàng xuống cấp, Member chọn FULL_COMPENSATION (F-036/API 95) - tài sản nền tảng, chờ thanh lý hoặc hủy
    PLATFORM_OWNED,
    // Hàng vẫn nằm trong kho nhưng tạm ẩn khỏi sàn chờ Member quyết định, dùng ở hai chỗ:
    // 1) Moderator phát hiện hàng xuống cấp lúc kiểm kho, mở DegradedItemCase trực tiếp (F-036) -
    //    chờ Member chọn FULL_COMPENSATION/PARTIAL_WITH_ITEM_BACK
    // 2) Sản phẩm hết 90 ngày (hết 3 kỳ ký gửi) - Điều 8.1 hợp đồng "hết 90 ngày, hàng được gỡ
    //    khỏi sàn" - chờ Member chọn TAKE_BACK/RE_CONSIGN ở EndOfTermDecision (F-029)
    WAREHOUSE
}
