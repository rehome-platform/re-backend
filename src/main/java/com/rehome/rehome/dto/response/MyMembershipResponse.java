package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.BillingCycle;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /memberships/me - gói hiện tại + hạn mức đã dùng. <b>Luôn trả 200</b>: Member chưa mua gói
 * thì trả tier {@code FREE} với {@code subscriptionId = null}, không trả 404.
 *
 * <p>Quy ước xuyên suốt: {@code limit* = null} nghĩa là KHÔNG GIỚI HẠN, và {@code remaining*}
 * tương ứng cũng là {@code null} -> mobile hiện chữ "Không giới hạn" và ẩn thanh progress.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyMembershipResponse {

    // null khi đang ở hạng FREE
    private String subscriptionId;
    private MembershipTier tier;
    private String tierName;
    // null khi FREE
    private SubscriptionStatus status;
    private BillingCycle billingCycle;
    private Instant startedAt;
    private Instant expiresAt;
    // null khi FREE; làm tròn lên nên gói hết hạn trong hôm nay vẫn hiện "còn 1 ngày"
    private Integer daysRemaining;
    private Quota quota;
    private VoucherSummary voucherSummary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Quota {
        private TryOnQuota liveTryon;
        private TryOnQuota aiTryon;
        private FeaturedListing featuredListing;
        private ConsignmentForms consignmentForms;
    }

    /**
     * Dùng chung cho cả thử đồ real-time và phối đồ AI. {@code maxSeconds} chỉ có nghĩa với
     * real-time, để null ở phối đồ AI.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TryOnQuota {
        private Integer limitPerDay;
        private long usedToday;
        private Integer remainingToday;
        private Integer limitPerMonth;
        // Chỉ để hiển thị tham khảo; KHÔNG dùng để chặn khi limitPerMonth = null
        private long usedThisMonth;
        private Integer remainingThisMonth;
        private Integer maxSeconds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FeaturedListing {
        private boolean enabled;
        // Số món đang thực sự nổi bật, cho banner "đang giúp N món của bạn hiển thị ưu tiên"
        private long productCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConsignmentForms {
        private int limit;
        // Số form đang chờ Moderator duyệt - cùng nguồn với pendingCount của API danh sách form
        private long pending;
        private int remaining;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VoucherSummary {
        private long unused;
        // Mã chưa dùng sẽ hết hạn trong 3 ngày tới
        private long expiringSoon;
    }
}
