package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MembershipTier;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipStatsResponse {

    private BigDecimal totalRevenue;
    private long activeSubscriptions;
    private long purchasesInRange;
    private long notRenewedInRange;

    @Builder.Default
    private List<MonthlyRevenue> revenueByMonth = List.of();

    @Builder.Default
    private List<TierBreakdown> byTier = List.of();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyRevenue {
        private String month;
        private MembershipTier tier;
        private BigDecimal revenue;
        private long purchases;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TierBreakdown {
        private MembershipTier tier;
        private String tierName;
        private BigDecimal revenue;
        private long purchases;
        private long activeSubscriptions;
        private BigDecimal voucherCost;
        private long liveTryonUsed;
        private long aiTryonUsed;
    }
}
