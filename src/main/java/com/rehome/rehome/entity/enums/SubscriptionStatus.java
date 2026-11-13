package com.rehome.rehome.entity.enums;

/**
 * Trạng thái một lần Member sở hữu gói.
 *
 * <ul>
 *   <li>{@code PENDING_PAYMENT} - đã tạo, chờ MoMo/VNPay báo về. Chưa có quyền lợi gì.</li>
 *   <li>{@code ACTIVE} - đang có hiệu lực. Mỗi Member chỉ được tối đa 1 dòng ở trạng thái này.</li>
 *   <li>{@code EXPIRED} - quá expiresAt, job 00:05 chuyển sang.</li>
 *   <li>{@code REPLACED} - Member mua gói KHÁC hạng khi còn hạn: gói cũ dừng ngay, không hoàn tiền.</li>
 *   <li>{@code CANCELLED} - Admin hủy tay kèm lý do.</li>
 * </ul>
 */
public enum SubscriptionStatus {
    PENDING_PAYMENT,
    ACTIVE,
    EXPIRED,
    REPLACED,
    CANCELLED
}
