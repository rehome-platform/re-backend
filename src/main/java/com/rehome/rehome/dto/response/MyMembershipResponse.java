package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.BillingCycle;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyMembershipResponse {

    private String subscriptionId;
    private MembershipTier tier;
    private String tierName;
    private SubscriptionStatus status;
    private BillingCycle billingCycle;
    private Instant startedAt;
    private Instant expiresAt;
    private Integer daysRemaining;
    private BigDecimal pricePaid;
    private BigDecimal remainingValue;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TryOnQuota {
        private Integer limitPerDay;
        private long usedToday;
        private Integer remainingToday;
        private Integer limitPerMonth;
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
        private long productCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConsignmentForms {
        private int limit;
        private long pending;
        private int remaining;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VoucherSummary {
        private long unused;
        private long expiringSoon;
    }
}
