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

/**
 * Hạn mức phối đồ AI (ảnh tĩnh) theo gói thành viên, tính <b>theo NGÀY</b>, reset 00:00 giờ VN.
 * Song song với {@link LiveTryOnQuotaGuard} nhưng đếm trên bảng {@code try_on_images}.
 *
 * <p>Đếm <b>số lượt sinh ảnh</b> trong ngày, không phải số ảnh đang còn trong bộ sưu tập: Member
 * xóa ảnh <b>không</b> được hoàn lượt. Giới hạn số ảnh trong bộ sưu tập là chuyện khác và cố ý
 * KHÔNG gắn vào gói (đặc tả §1.4).
 *
 * <p>Khác {@code LiveTryOnQuotaGuard}: ở đây không có bước "reserve" - lượt được tính bằng chính
 * row ảnh được lưu sau khi sinh thành công, nên {@link #assertCanGenerate} chỉ kiểm tra trước khi
 * gọi AI. Hệ quả có chủ ý: sinh ảnh lỗi (502) thì không mất lượt.
 */
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

        // Còn quota của gói (chưa tính lượt thưởng mua bằng điểm)
        public boolean withinPlanQuota() {
            return !MembershipQuotaResolver.exhausted(quota.aiTryonPerDay(), usedToday)
                    && !MembershipQuotaResolver.exhausted(quota.aiTryonPerMonth(), usedThisMonth);
        }

        // Hết quota gói vẫn sinh được nếu còn lượt thưởng
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

    /**
     * Gọi TRƯỚC khi gửi request sang AI server, để chặn chi phí thật trước khi phát sinh.
     *
     * @return true nếu lượt này phải trả bằng lượt thưởng (quota gói đã hết) - caller gọi
     *         {@link #consumeBonusCredit} sau khi lưu ảnh thành công. Giữ đúng nguyên tắc cũ: sinh
     *         ảnh lỗi (502) không mất lượt, kể cả lượt thưởng.
     */
    @Transactional(readOnly = true)
    public boolean assertCanGenerate(UUID memberId) {
        Usage usage = usage(memberId);
        if (usage.withinPlanQuota()) {
            return false;
        }
        if (usage.bonusCredits() > 0) {
            return true;
        }
        throw new AppException(ErrorCode.TRYON_IMAGE_QUOTA_EXCEEDED);                                // 429
    }

    /** Trừ 1 lượt thưởng bằng UPDATE nguyên tử; false nếu request khác đã dùng mất lượt cuối. */
    @Transactional
    public boolean consumeBonusCredit(UUID memberId) {
        return memberProfileRepository.consumeAiTryonCredit(memberId) > 0;
    }
}
