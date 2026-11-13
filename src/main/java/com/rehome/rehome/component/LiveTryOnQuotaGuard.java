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
import com.rehome.rehome.util.VnTimeWindow;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đếm + trừ quota thử đồ AI real-time trong một bean riêng, TÁCH khỏi {@code LiveTryOnServiceImpl}:
 * method {@code @Transactional} chỉ có hiệu lực khi được gọi qua proxy Spring, tức là qua một bean
 * KHÁC - gọi {@code this.method()} trong cùng class (self-invocation) sẽ bỏ qua proxy và chạy không
 * transaction. Tách ra đây để {@link #reserve} thực sự atomic.
 *
 * <p><b>Hạn mức đọc theo gói thành viên</b> ({@link MembershipQuotaResolver}), không còn
 * {@code @Value("${tryon.live.daily-limit}")}: Admin sửa số lượt ở màn W-01 và người đã mua gói
 * giữ snapshot của mình. Hạn mức chính là <b>theo NGÀY</b> (reset 00:00 giờ VN); trần theo tháng là
 * tùy chọn, seed {@code null} nên mặc định không chặn.
 *
 * <p>Vẫn không chặn 100% race nếu cùng 1 account bắn 2 request start gần như đồng thời (đếm xong
 * ở 2 transaction song song trước khi transaction nào kịp insert) - chấp nhận cho MVP, lệch tối
 * đa 1 lượt, không cần thêm bảng counter riêng + khoá dòng.
 */
@Component
@RequiredArgsConstructor
public class LiveTryOnQuotaGuard {

    private final LiveTryOnSessionRepository liveTryOnSessionRepository;
    private final MembershipQuotaResolver quotaResolver;

    /** Ảnh chụp hạn mức + số đã dùng của một account, đủ để dựng response GET /tryon/live/quota. */
    public record Usage(
            EffectiveQuota quota,
            long usedToday,
            long usedThisMonth) {

        public Integer remainingToday() {
            return MembershipQuotaResolver.remaining(quota.liveTryonPerDay(), usedToday);
        }

        public Integer remainingThisMonth() {
            return MembershipQuotaResolver.remaining(quota.liveTryonPerMonth(), usedThisMonth);
        }

        // canStart = false khi hết lượt ngày HOẶC hết lượt tháng (nếu có trần tháng)
        public boolean canStart() {
            return !MembershipQuotaResolver.exhausted(quota.liveTryonPerDay(), usedToday)
                    && !MembershipQuotaResolver.exhausted(quota.liveTryonPerMonth(), usedThisMonth);
        }
    }

    @Transactional(readOnly = true)
    public Usage usage(UUID memberId) {
        return new Usage(quotaResolver.resolve(memberId), countToday(memberId), countThisMonth(memberId));
    }

    public long countToday(UUID memberId) {
        VnTimeWindow.Window w = VnTimeWindow.today();
        return liveTryOnSessionRepository.countByMember_IdAndCreatedAtBetween(memberId, w.from(), w.to());
    }

    public long countThisMonth(UUID memberId) {
        VnTimeWindow.Window w = VnTimeWindow.thisMonth();
        return liveTryOnSessionRepository.countByMember_IdAndCreatedAtBetween(memberId, w.from(), w.to());
    }

    /** Hạng thấp nhất có hạn mức real-time cao hơn hạng hiện tại; null = đã cao nhất. */
    public com.rehome.rehome.entity.enums.MembershipTier upsellTier(EffectiveQuota quota) {
        return quotaResolver.findUpsellTier(quota.tier(), quota.liveTryonPerDay(),
                MembershipPlan::getLiveTryonPerDay);
    }

    // Đây là nơi quota THỰC SỰ bị trừ: tạo row STARTED ngay trước khi gọi Decart mint token,
    // không đợi mobile xác nhận kết nối camera thành công (xem DecartVtonClient).
    @Transactional
    public LiveTryOnSession reserve(Account account, Product product, String modelId) {
        Usage usage = usage(account.getId());
        if (!usage.canStart()) {
            throw new AppException(ErrorCode.TRYON_LIVE_QUOTA_EXCEEDED);                             // 429
        }
        return liveTryOnSessionRepository.save(LiveTryOnSession.builder()
                .member(account)
                .product(product)
                .modelId(modelId)
                .status(LiveTryOnStatus.STARTED)
                .build());
    }
}
