package com.rehome.rehome.component;

import com.rehome.rehome.component.MembershipQuotaResolver.EffectiveQuota;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.membership.MembershipPlan;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
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

    public record Usage(
            EffectiveQuota quota,
            long usedToday,
            long usedThisMonth) {

        public Integer remainingToday() {
            return MembershipQuotaResolver.remaining(quota.aiTryonPerDay(), usedToday);
        }

        public Integer remainingThisMonth() {
            return MembershipQuotaResolver.remaining(quota.aiTryonPerMonth(), usedThisMonth);
        }

        public boolean canGenerate() {
            return !MembershipQuotaResolver.exhausted(quota.aiTryonPerDay(), usedToday)
                    && !MembershipQuotaResolver.exhausted(quota.aiTryonPerMonth(), usedThisMonth);
        }
    }

    @Transactional(readOnly = true)
    public Usage usage(UUID memberId) {
        return new Usage(quotaResolver.resolve(memberId), countToday(memberId), countThisMonth(memberId));
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

    /** Gọi TRƯỚC khi gửi request sang AI server, để chặn chi phí thật trước khi phát sinh. */
    @Transactional(readOnly = true)
    public void assertCanGenerate(UUID memberId) {
        if (!usage(memberId).canGenerate()) {
            throw new AppException(ErrorCode.TRYON_IMAGE_QUOTA_EXCEEDED);                            // 429
        }
    }
}
