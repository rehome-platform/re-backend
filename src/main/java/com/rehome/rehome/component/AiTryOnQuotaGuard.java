package com.rehome.rehome.component;

import com.rehome.rehome.component.MembershipQuotaResolver.EffectiveQuota;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.membership.MembershipPlan;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.TryOnImageRepository;
import com.rehome.rehome.util.VnTimeWindow;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AiTryOnQuotaGuard {

    private final TryOnImageRepository tryOnImageRepository;
    private final MembershipQuotaResolver quotaResolver;
    private final MemberProfileRepository memberProfileRepository;

    public record Usage(
            EffectiveQuota quota,
            long usedToday,
            long usedThisMonth,
            int bonusCredits) {

        public Integer remainingToday() {
            return MembershipQuotaResolver.remaining(quota.aiTryonPerDay(), usedToday);
        }

        public Integer remainingThisMonth() {
            return MembershipQuotaResolver.remaining(quota.aiTryonPerMonth(), usedThisMonth);
        }

        public boolean withinPlanQuota() {
            return !MembershipQuotaResolver.exhausted(quota.aiTryonPerDay(), usedToday)
                    && !MembershipQuotaResolver.exhausted(quota.aiTryonPerMonth(), usedThisMonth);
        }

        public boolean canGenerate() {
            return withinPlanQuota() || bonusCredits > 0;
        }
    }

    @Transactional(readOnly = true)
    public Usage usage(UUID memberId) {
        int bonus = memberProfileRepository.findBonusAiTryonCredits(memberId).orElse(0);
        return new Usage(quotaResolver.resolve(memberId), countToday(memberId), countThisMonth(memberId), bonus);
    }

    public long countToday(UUID memberId) {
        VnTimeWindow.Window w = VnTimeWindow.today();
        return tryOnImageRepository.countByMember_IdAndCreatedAtBetween(memberId, w.from(), w.to());
    }

    public long countThisMonth(UUID memberId) {
        VnTimeWindow.Window w = VnTimeWindow.thisMonth();
        return tryOnImageRepository.countByMember_IdAndCreatedAtBetween(memberId, w.from(), w.to());
    }

    public MembershipTier upsellTier(EffectiveQuota quota) {
        return quotaResolver.findUpsellTier(quota.tier(), quota.aiTryonPerDay(),
                MembershipPlan::getAiTryonPerDay);
    }

    @Transactional(readOnly = true)
    public boolean assertCanGenerate(UUID memberId) {
        Usage usage = usage(memberId);
        if (usage.withinPlanQuota()) {
            return false;
        }
        if (usage.bonusCredits() > 0) {
            return true;
        }
        throw new AppException(ErrorCode.TRYON_IMAGE_QUOTA_EXCEEDED);
    }

    @Transactional
    public boolean consumeBonusCredit(UUID memberId) {
        return memberProfileRepository.consumeAiTryonCredit(memberId) > 0;
    }
}
