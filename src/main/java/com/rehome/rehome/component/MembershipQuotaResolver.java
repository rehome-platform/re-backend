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

/**
 * Một chỗ duy nhất trả lời "hạn mức hiện tại của account này là bao nhiêu".
 *
 * <p>Thứ tự ưu tiên:
 * <ol>
 *   <li>Subscription {@code ACTIVE} còn hạn -> dùng <b>snapshot trên subscription</b>, không đọc
 *       lại plan. Admin hạ hạn mức sau khi Member đã trả tiền thì người đó vẫn giữ mức cũ.</li>
 *   <li>Không có -> dòng {@code FREE} trong {@code membership_plans}.</li>
 *   <li>Không có cả dòng FREE (DB chưa seed) -> {@link #FALLBACK} để API không sập; ghi WARN.</li>
 * </ol>
 *
 * <p>Mọi guard hạn mức (thử đồ real-time, phối đồ AI, form ký gửi) đều phải đi qua đây thay vì
 * đọc {@code @Value} hay {@code system_configs} - nếu không sẽ có hai nguồn sự thật.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipQuotaResolver {

    /**
     * Hạn mức đang áp dụng cho một account. {@code null} ở các trường {@code *PerDay}/{@code *PerMonth}
     * nghĩa là KHÔNG GIỚI HẠN (mobile hiện "Không giới hạn", không hiện thanh progress).
     *
     * @param subscriptionId null khi đang ở hạng FREE
     */
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

    /**
     * Chỉ dùng khi {@code membership_plans} chưa có dòng FREE - tức initializer chưa chạy hoặc DB
     * bị xóa tay. Trùng với giá trị seed của hạng FREE để hành vi không đổi, nhưng đây là lưới an
     * toàn, KHÔNG phải nguồn cấu hình: đừng sửa số ở đây để đổi hạn mức, hãy sửa ở W-01.
     */
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

    /** Subscription đã nạp sẵn (ví dụ trong luồng mua gói) thì không cần query lại. */
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

    /**
     * Hạng thấp nhất đang bán mà có hạn mức cao hơn hạng hiện tại, để mobile hiện nút
     * "Nâng cấp {upsellTier} để thử thêm". null = đã ở mức cao nhất hoặc không còn gói nào bán.
     *
     * @param currentLimit hạn mức/ngày đang áp dụng; null = đang không giới hạn -> không upsell
     * @param limitOfPlan  lấy hạn mức/ngày tương ứng ra khỏi plan (live hay ai tùy nơi gọi)
     */
    @Transactional(readOnly = true)
    public MembershipTier findUpsellTier(MembershipTier currentTier, Integer currentLimit,
                                         java.util.function.Function<MembershipPlan, Integer> limitOfPlan) {
        if (currentLimit == null) {
            return null;   // đang không giới hạn, không có gì để nâng cấp thêm
        }
        List<MembershipPlan> candidates =
                new ArrayList<>(membershipPlanRepository.findAllByActiveTrueAndPurchasableTrueOrderBySortOrderAsc());
        candidates.sort(Comparator.comparingInt(p -> p.getTier().ordinal()));

        for (MembershipPlan p : candidates) {
            if (p.getTier().ordinal() <= currentTier.ordinal()) {
                continue;
            }
            Integer limit = limitOfPlan.apply(p);
            // null = không giới hạn -> chắc chắn cao hơn
            if (limit == null || limit > currentLimit) {
                return p.getTier();
            }
        }
        return null;
    }

    /** Dùng khi chỉ cần biết còn bao nhiêu lượt: null limit = không giới hạn -> trả null. */
    public static Integer remaining(Integer limit, long used) {
        return limit == null ? null : (int) Math.max(0, limit - used);
    }

    /** Hết lượt chưa. limit null = không giới hạn -> luôn còn. */
    public static boolean exhausted(Integer limit, long used) {
        return limit != null && used >= limit;
    }
}
