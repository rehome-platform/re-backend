package com.rehome.rehome.component;

import com.rehome.rehome.component.MembershipQuotaResolver.EffectiveQuota;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.LiveTryOnStatus;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.membership.MembershipPlan;
import com.rehome.rehome.entity.tryon.LiveTryOnSession;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.LiveTryOnSessionRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.util.VnTimeWindow;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LiveTryOnQuotaGuard {

    private final LiveTryOnSessionRepository liveTryOnSessionRepository;
    private final MembershipQuotaResolver quotaResolver;
    private final MemberProfileRepository memberProfileRepository;
    private final com.rehome.rehome.repository.AccountRepository accountRepository;

    @org.springframework.beans.factory.annotation.Value("${tryon.live.max-unconfirmed-per-day:5}")
    private int maxUnconfirmedPerDay = 5;

    public record Usage(
            EffectiveQuota quota,
            long usedToday,
            long usedThisMonth,
            int bonusCredits) {

        public Integer remainingToday() {
            return MembershipQuotaResolver.remaining(quota.liveTryonPerDay(), usedToday);
        }

        public Integer remainingThisMonth() {
            return MembershipQuotaResolver.remaining(quota.liveTryonPerMonth(), usedThisMonth);
        }

        public boolean withinPlanQuota() {
            return !MembershipQuotaResolver.exhausted(quota.liveTryonPerDay(), usedToday)
                    && !MembershipQuotaResolver.exhausted(quota.liveTryonPerMonth(), usedThisMonth);
        }

        public boolean canStart() {
            return withinPlanQuota() || bonusCredits > 0;
        }
    }

    @Transactional(readOnly = true)
    public Usage usage(UUID memberId) {
        int bonus = memberProfileRepository.findBonusLiveTryonCredits(memberId).orElse(0);
        return new Usage(quotaResolver.resolve(memberId), countToday(memberId), countThisMonth(memberId), bonus);
    }

    public long countToday(UUID memberId) {
        VnTimeWindow.Window w = VnTimeWindow.today();
        return liveTryOnSessionRepository.countByMember_IdAndFirstFrameAtGreaterThanEqualAndFirstFrameAtLessThan(memberId, w.from(), w.to());
    }

    public long countThisMonth(UUID memberId) {
        VnTimeWindow.Window w = VnTimeWindow.thisMonth();
        return liveTryOnSessionRepository.countByMember_IdAndFirstFrameAtGreaterThanEqualAndFirstFrameAtLessThan(memberId, w.from(), w.to());
    }

    public com.rehome.rehome.entity.enums.MembershipTier upsellTier(EffectiveQuota quota) {
        return quotaResolver.findUpsellTier(quota.tier(), quota.liveTryonPerDay(),
                MembershipPlan::getLiveTryonPerDay);
    }

    @Transactional
    public LiveTryOnSession reserve(Account account, Product product, String modelId) {
        lockMember(account.getId());
        if (!usage(account.getId()).canStart()) {
            throw new AppException(ErrorCode.TRYON_LIVE_QUOTA_EXCEEDED);
        }
        VnTimeWindow.Window today = VnTimeWindow.today();
        long unconfirmed = liveTryOnSessionRepository
                .countByMember_IdAndFirstFrameAtIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        account.getId(), today.from(), today.to());
        if (unconfirmed >= maxUnconfirmedPerDay) {
            throw new AppException(ErrorCode.TRYON_LIVE_UNCONFIRMED_LIMIT);
        }
        return liveTryOnSessionRepository.save(LiveTryOnSession.builder()
                .member(account)
                .product(product)
                .modelId(modelId)
                .status(LiveTryOnStatus.STARTED)
                .build());
    }

    @Transactional
    public void confirmFirstFrame(Account account, UUID sessionId) {
        lockMember(account.getId());
        LiveTryOnSession session = liveTryOnSessionRepository.findByIdAndMember_Id(sessionId, account.getId())
                .orElseThrow(() -> new AppException(ErrorCode.TRYON_LIVE_SESSION_NOT_FOUND));
        if (session.getFirstFrameAt() != null) {
            return;
        }
        if (session.getStatus() == LiveTryOnStatus.FAILED) {
            throw new AppException(ErrorCode.TRYON_LIVE_SESSION_ALREADY_FINISHED);
        }
        if (!usage(account.getId()).withinPlanQuota()
                && memberProfileRepository.consumeLiveTryonCredit(account.getId()) == 0) {
            throw new AppException(ErrorCode.TRYON_LIVE_QUOTA_EXCEEDED);
        }
        session.setFirstFrameAt(java.time.Instant.now());
    }

    private void lockMember(UUID memberId) {
        accountRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }
}
