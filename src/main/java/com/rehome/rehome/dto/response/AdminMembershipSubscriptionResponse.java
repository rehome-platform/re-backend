package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.BillingCycle;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.PaymentStatus;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/admin/membership-subscriptions (dòng danh sách, W-02) và
 * GET /api/admin/membership-subscriptions/{id} (chi tiết, W-03 - trả thêm 3 khối cuối).
 *
 * <p>Dùng một DTO cho cả hai: các khối {@code quotaUsage} / {@code payments} / {@code vouchers}
 * để null ở danh sách. Tách thành hai class sẽ phải lặp lại 12 field đầu.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminMembershipSubscriptionResponse {

    private String id;
    private String memberId;
    private String memberEmail;
    private String memberName;
    private String memberCode;
    private MembershipTier tier;
    private String tierName;
    private SubscriptionStatus status;
    private BillingCycle billingCycle;
    private BigDecimal pricePaid;
    private Instant startedAt;
    private Instant expiresAt;
    private Integer daysRemaining;
    // true = Admin tặng tay, không phải Member mua
    private boolean grantedByAdmin;
    private String cancelReason;

    // ---- Chỉ có ở chi tiết {id} ----

    private QuotaUsage quotaUsage;
    private List<PaymentRef> payments;
    private List<VoucherRef> vouchers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuotaUsage {
        private long liveTryonUsedToday;
        private long liveTryonUsedThisMonth;
        private long aiTryonUsedToday;
        private long aiTryonUsedThisMonth;
        private boolean featuredListing;
        private long featuredProductCount;
        private long pendingConsignmentForms;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentRef {
        private BigDecimal amount;
        private OrderPaymentMethod method;
        private PaymentStatus status;
        private Instant paidAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VoucherRef {
        private String code;
        private String name;
        private String status;
        @Builder.Default
        private List<String> usedOrderNos = List.of();
    }
}
