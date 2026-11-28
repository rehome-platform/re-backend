package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.MembershipStatsResponse;
import com.rehome.rehome.dto.response.VoucherStatsResponse;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.membership.MembershipPlan;
import com.rehome.rehome.entity.membership.VoucherTemplate;
import com.rehome.rehome.repository.MembershipPlanRepository;
import com.rehome.rehome.repository.MembershipStatsQueryRepository;
import com.rehome.rehome.repository.MembershipStatsQueryRepository.PaymentRow;
import com.rehome.rehome.repository.MembershipStatsQueryRepository.TemplateStatusCount;
import com.rehome.rehome.repository.VoucherTemplateRepository;
import com.rehome.rehome.service.MembershipStatsService;
import com.rehome.rehome.util.AdminDateRange;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipStatsServiceImpl implements MembershipStatsService {

    private final MembershipStatsQueryRepository statsRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final VoucherTemplateRepository voucherTemplateRepository;

    @Override
    public MembershipStatsResponse getMembershipStats(AdminDateRange range) {
        Instant now = Instant.now();

        List<PaymentRow> payments = statsRepository.successfulPayments(range.from(), range.to());

        BigDecimal totalRevenue = payments.stream()
                .map(PaymentRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<MembershipTier, Long> activeByTier = statsRepository.activeCountByTier(now);
        Map<MembershipTier, BigDecimal> voucherCostByTier =
                statsRepository.voucherCostByTier(range.from(), range.to());
        Map<MembershipTier, Long> liveUsage =
                statsRepository.liveTryOnUsageByTier(range.from(), range.to(), now);
        Map<MembershipTier, Long> aiUsage =
                statsRepository.aiTryOnUsageByTier(range.from(), range.to(), now);

        List<MembershipStatsResponse.MonthlyRevenue> revenueByMonth = new ArrayList<>();
        Map<String, Map<MembershipTier, BigDecimal>> grouped =
                statsRepository.revenueByMonthAndTier(payments);
        Map<String, Map<MembershipTier, Long>> purchaseCounts = countPurchasesByMonthAndTier(payments);

        grouped.forEach((month, byTier) -> byTier.forEach((tier, revenue) ->
                revenueByMonth.add(MembershipStatsResponse.MonthlyRevenue.builder()
                        .month(month)
                        .tier(tier)
                        .revenue(revenue)
                        .purchases(purchaseCounts
                                .getOrDefault(month, Map.of())
                                .getOrDefault(tier, 0L))
                        .build())));

        List<MembershipStatsResponse.TierBreakdown> byTier = new ArrayList<>();
        for (MembershipPlan plan : membershipPlanRepository.findAllByOrderBySortOrderAsc()) {
            if (plan.getTier() == MembershipTier.FREE) {
                continue;
            }
            MembershipTier tier = plan.getTier();
            BigDecimal revenue = payments.stream()
                    .filter(p -> p.tier() == tier)
                    .map(PaymentRow::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long purchases = payments.stream().filter(p -> p.tier() == tier).count();

            byTier.add(MembershipStatsResponse.TierBreakdown.builder()
                    .tier(tier)
                    .tierName(plan.getName())
                    .revenue(revenue)
                    .purchases(purchases)
                    .activeSubscriptions(activeByTier.getOrDefault(tier, 0L))
                    .voucherCost(voucherCostByTier.getOrDefault(tier, BigDecimal.ZERO))
                    .liveTryonUsed(liveUsage.getOrDefault(tier, 0L))
                    .aiTryonUsed(aiUsage.getOrDefault(tier, 0L))
                    .build());
        }

        return MembershipStatsResponse.builder()
                .totalRevenue(totalRevenue)
                .activeSubscriptions(activeByTier.values().stream().mapToLong(Long::longValue).sum())
                .purchasesInRange(payments.size())
                .notRenewedInRange(statsRepository.notRenewedCount(range.from(), range.to()))
                .revenueByMonth(revenueByMonth)
                .byTier(byTier)
                .build();
    }

    private static Map<String, Map<MembershipTier, Long>> countPurchasesByMonthAndTier(
            List<PaymentRow> payments) {
        Map<String, Map<MembershipTier, Long>> counts = new LinkedHashMap<>();
        for (PaymentRow row : payments) {
            counts.computeIfAbsent(MembershipStatsQueryRepository.monthKey(row.paidAt()),
                            k -> new EnumMap<>(MembershipTier.class))
                    .merge(row.tier(), 1L, Long::sum);
        }
        return counts;
    }

    @Override
    public VoucherStatsResponse getVoucherStats(AdminDateRange range) {
        List<TemplateStatusCount> counts =
                statsRepository.voucherCountsByTemplate(range.from(), range.to());

        Map<UUID, Map<MemberVoucherStatus, Long>> byTemplate = new LinkedHashMap<>();
        for (TemplateStatusCount row : counts) {
            byTemplate.computeIfAbsent(row.templateId(), k -> new EnumMap<>(MemberVoucherStatus.class))
                    .merge(row.status(), row.count(), Long::sum);
        }

        long totalIssued = 0;
        long totalUsed = 0;
        long totalExpired = 0;
        List<VoucherStatsResponse.TemplateStats> templateStats = new ArrayList<>();

        for (VoucherTemplate template : voucherTemplateRepository.findAllByOrderByCreatedAtAsc()) {
            Map<MemberVoucherStatus, Long> statuses =
                    byTemplate.getOrDefault(template.getId(), Map.of());
            long unused = statuses.getOrDefault(MemberVoucherStatus.UNUSED, 0L);
            long used = statuses.getOrDefault(MemberVoucherStatus.USED, 0L);
            long expired = statuses.getOrDefault(MemberVoucherStatus.EXPIRED, 0L);
            long issued = unused + used + expired;

            totalIssued += issued;
            totalUsed += used;
            totalExpired += expired;

            templateStats.add(VoucherStatsResponse.TemplateStats.builder()
                    .templateId(template.getId().toString())
                    .name(template.getName())
                    .type(template.getType())
                    .issued(issued)
                    .used(used)
                    .expired(expired)
                    .usageRatePercent(usageRate(used, issued))
                    .build());
        }

        return VoucherStatsResponse.builder()
                .totalIssued(totalIssued)
                .totalUsed(totalUsed)
                .totalExpired(totalExpired)
                .totalSpent(statsRepository.totalVoucherSpent(range.from(), range.to()))
                .byTemplate(templateStats)
                .build();
    }

    private static BigDecimal usageRate(long used, long issued) {
        if (issued <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(used)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(issued), 1, RoundingMode.HALF_UP);
    }
}
