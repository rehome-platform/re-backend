package com.rehome.rehome.component;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.membership.MemberSubscription;
import com.rehome.rehome.entity.membership.MembershipPlan;
import com.rehome.rehome.repository.MemberSubscriptionRepository;
import com.rehome.rehome.repository.MembershipPlanRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipQuotaResolver {

    public record EffectiveQuota(
            UUID subscriptionId,
            MembershipTier tier,
            String tierName,
            Integer liveTryonPerDay,
            Integer liveTryonPerMonth,
            int liveTryonMaxSeconds,
            Integer aiTryonPerDay,
            Integer aiTryonPerMonth,
            boolean featuredListing,
            int maxPendingConsignmentForms,
            Instant startedAt,
            Instant expiresAt) {

        public boolean isFree() {
            return tier == MembershipTier.FREE;
        }
    }

    private static final EffectiveQuota FALLBACK = new EffectiveQuota(
            null, MembershipTier.FREE, "Thành viên thường",
            2, null, 10,
            5, null,
            false, 5,
            null, null);

    private final MemberSubscriptionRepository memberSubscriptionRepository;
    private final MembershipPlanRepository membershipPlanRepository;

    @Transactional(readOnly = true)
    public EffectiveQuota resolve(UUID accountId) {
        Instant now = Instant.now();

        MemberSubscription active = memberSubscriptionRepository.findActiveByMemberId(accountId)
                .filter(s -> s.isEffectiveAt(now))
                .orElse(null);

        if (active != null) {
            return fromSubscription(active);
        }
        return freeQuota();
    }

    public EffectiveQuota fromSubscription(MemberSubscription s) {
        return new EffectiveQuota(
                s.getId(),
                s.getTier(),
                s.getPlan().getName(),
                s.getLiveTryonPerDay(),
                s.getLiveTryonPerMonth(),
                s.getLiveTryonMaxSeconds(),
                s.getAiTryonPerDay(),
                s.getAiTryonPerMonth(),
                Boolean.TRUE.equals(s.getFeaturedListing()),
                s.getMaxPendingConsignmentForms(),
                s.getStartedAt(),
                s.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public EffectiveQuota freeQuota() {
        Optional<MembershipPlan> free = membershipPlanRepository.findByTier(MembershipTier.FREE);
        if (free.isEmpty()) {
            log.warn(">>>> membership_plans chua co dong FREE - dung han muc du phong cua "
                    + "MembershipQuotaResolver. Kiem tra MembershipPlanInitializer.");
            return FALLBACK;
        }
        MembershipPlan p = free.get();
        return new EffectiveQuota(
                null,
                p.getTier(),
                p.getName(),
                p.getLiveTryonPerDay(),
                p.getLiveTryonPerMonth(),
                p.getLiveTryonMaxSeconds(),
                p.getAiTryonPerDay(),
                p.getAiTryonPerMonth(),
                Boolean.TRUE.equals(p.getFeaturedListing()),
                p.getMaxPendingConsignmentForms(),
                null, null);
    }

    @Transactional(readOnly = true)
    public MembershipTier findUpsellTier(MembershipTier currentTier, Integer currentLimit,
                                         java.util.function.Function<MembershipPlan, Integer> limitOfPlan) {
        if (currentLimit == null) {
            return null;
        }
        List<MembershipPlan> candidates =
                new ArrayList<>(membershipPlanRepository.findAllByActiveTrueAndPurchasableTrueOrderBySortOrderAsc());
        candidates.sort(Comparator.comparingInt(p -> p.getTier().ordinal()));

        for (MembershipPlan p : candidates) {
            if (p.getTier().ordinal() <= currentTier.ordinal()) {
                continue;
            }
            Integer limit = limitOfPlan.apply(p);
            if (limit == null || limit > currentLimit) {
                return p.getTier();
            }
        }
        return null;
    }

    public static Integer remaining(Integer limit, long used) {
        return limit == null ? null : (int) Math.max(0, limit - used);
    }

    public static boolean exhausted(Integer limit, long used) {
        return limit != null && used >= limit;
    }
}
