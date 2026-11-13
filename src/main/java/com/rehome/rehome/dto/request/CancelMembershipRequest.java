package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/admin/membership-subscriptions/{id}/cancel.
 *
 * <p>Hủy gói <b>không hoàn tiền tự động</b>: hoàn tiền là quyết định riêng, Admin làm qua luồng ví.
 * Hủy chỉ dừng quyền lợi (và tắt nhãn "Nổi bật" của mọi món). Voucher đã phát vẫn dùng được tới
 * khi hết hạn của chính nó.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelMembershipRequest {

    // Bắt buộc - ghi vào member_subscriptions.cancel_reason và audit log CANCEL_MEMBERSHIP
    private String reason;
}
