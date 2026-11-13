package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /memberships/subscribe.
 *
 * <ul>
 *   <li>Trả ví -> {@code status = ACTIVE}, {@code payUrl = null}, gói có hiệu lực ngay và
 *       {@code vouchersIssued} là số mã vừa phát đợt đầu.</li>
 *   <li>Trả MoMo/VNPay -> {@code status = PENDING_PAYMENT}, {@code expiresAt = null},
 *       {@code payUrl} để mobile mở WebView. Mobile <b>không được tin</b> kết quả cổng hiển thị,
 *       phải gọi lại {@code GET /memberships/me} sau khi callback về.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscribeMembershipResponse {

    private String subscriptionId;
    private SubscriptionStatus status;
    private MembershipTier tier;
    private String tierName;
    // null khi còn chờ thanh toán
    private Instant expiresAt;
    // null khi trả bằng ví
    private String payUrl;
    // Số voucher phát ngay đợt đầu; 0 khi còn chờ thanh toán
    private int vouchersIssued;
    // Khác null khi Member đang có gói khác hạng còn hạn và gói đó vừa bị chuyển REPLACED -
    // mobile dùng để hiện lại cảnh báo "không hoàn phần còn lại" ở màn kết quả
    private MembershipTier replacedTier;
}
