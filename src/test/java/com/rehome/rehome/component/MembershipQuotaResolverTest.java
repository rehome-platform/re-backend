package com.rehome.rehome.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.rehome.rehome.component.MembershipQuotaResolver.EffectiveQuota;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.entity.membership.MemberSubscription;
import com.rehome.rehome.entity.membership.MembershipPlan;
import com.rehome.rehome.repository.MemberSubscriptionRepository;
import com.rehome.rehome.repository.MembershipPlanRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Một chỗ duy nhất trả lời "hạn mức hiện tại của account này là bao nhiêu". Hai điểm dễ sai nhất:
 * <ul>
 *   <li>Hạn mức phải đọc từ <b>snapshot trên subscription</b>, không đọc lại plan - Admin hạ hạn
 *       mức không được cắt quyền lợi của người đã trả tiền.</li>
 *   <li>Dòng {@code ACTIVE} đã quá {@code expiresAt} (job dọn chỉ chạy 00:05) phải bị coi là hết
 *       hạn ngay, không được tiếp tục cho quyền lợi tới sáng.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipQuotaResolverTest {

    @Mock private MemberSubscriptionRepository memberSubscriptionRepository;
    @Mock private MembershipPlanRepository membershipPlanRepository;

    @InjectMocks
    private MembershipQuotaResolver resolver;

    private UUID accountId;
    private MembershipPlan freePlan;
    private MembershipPlan goldPlan;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        freePlan = plan(MembershipTier.FREE, "Thành viên thường", 2, 10, 5, false, 5, false);
        goldPlan = plan(MembershipTier.GOLD, "Thành viên Vàng", 10, 15, 40, true, 15, true);

        when(membershipPlanRepository.findByTier(MembershipTier.FREE)).thenReturn(Optional.of(freePlan));
    }

    @Test
    @DisplayName("Chưa mua gói -> đọc hạn mức từ dòng FREE trong membership_plans, subscriptionId null")
    void noSubscription_fallsBackToFreePlan() {
        when(memberSubscriptionRepository.findActiveByMemberId(accountId)).thenReturn(Optional.empty());

        EffectiveQuota quota = resolver.resolve(accountId);

        assertThat(quota.tier()).isEqualTo(MembershipTier.FREE);
        assertThat(quota.isFree()).isTrue();
        assertThat(quota.subscriptionId()).isNull();
        assertThat(quota.liveTryonPerDay()).isEqualTo(2);
        assertThat(quota.aiTryonPerDay()).isEqualTo(5);
        assertThat(quota.maxPendingConsignmentForms()).isEqualTo(5);
        assertThat(quota.featuredListing()).isFalse();
    }

    @Test
    @DisplayName("Có gói ACTIVE còn hạn -> đọc SNAPSHOT trên subscription, KHÔNG đọc lại plan")
    void activeSubscription_usesItsOwnSnapshot() {
        MemberSubscription s = subscription(goldPlan, Instant.now().plus(10, ChronoUnit.DAYS));
        // Admin đã HẠ hạn mức của plan sau khi Member mua
        goldPlan.setLiveTryonPerDay(3);
        goldPlan.setAiTryonPerDay(7);
        when(memberSubscriptionRepository.findActiveByMemberId(accountId)).thenReturn(Optional.of(s));

        EffectiveQuota quota = resolver.resolve(accountId);

        assertThat(quota.tier()).isEqualTo(MembershipTier.GOLD);
        assertThat(quota.subscriptionId()).isEqualTo(s.getId());
        // Giữ mức đã mua, không bị hạ theo plan
        assertThat(quota.liveTryonPerDay()).isEqualTo(10);
        assertThat(quota.aiTryonPerDay()).isEqualTo(40);
    }

    @Test
    @DisplayName("Dòng vẫn ACTIVE nhưng đã quá expiresAt (job 00:05 chưa chạy) -> coi như hết hạn, về FREE")
    void activeButExpired_isTreatedAsFree() {
        MemberSubscription s = subscription(goldPlan, Instant.now().minus(1, ChronoUnit.HOURS));
        when(memberSubscriptionRepository.findActiveByMemberId(accountId)).thenReturn(Optional.of(s));

        EffectiveQuota quota = resolver.resolve(accountId);

        assertThat(quota.tier()).isEqualTo(MembershipTier.FREE);
        assertThat(quota.subscriptionId()).isNull();
    }

    @Test
    @DisplayName("membership_plans chưa seed dòng FREE -> dùng hạn mức dự phòng, API không sập")
    void missingFreePlan_usesFallback() {
        when(membershipPlanRepository.findByTier(MembershipTier.FREE)).thenReturn(Optional.empty());
        when(memberSubscriptionRepository.findActiveByMemberId(accountId)).thenReturn(Optional.empty());

        EffectiveQuota quota = resolver.resolve(accountId);

        assertThat(quota.tier()).isEqualTo(MembershipTier.FREE);
        assertThat(quota.liveTryonPerDay()).isEqualTo(2);
        assertThat(quota.maxPendingConsignmentForms()).isEqualTo(5);
    }

    @Test
    @DisplayName("limit = null nghĩa là KHÔNG GIỚI HẠN: remaining cũng null và không bao giờ hết lượt")
    void nullLimit_meansUnlimited() {
        assertThat(MembershipQuotaResolver.remaining(null, 9999)).isNull();
        assertThat(MembershipQuotaResolver.exhausted(null, 9999)).isFalse();
    }

    @Test
    @DisplayName("remaining không bao giờ âm, kể cả khi đã dùng quá hạn mức (Admin hạ mức giữa ngày)")
    void remaining_isClampedAtZero() {
        assertThat(MembershipQuotaResolver.remaining(5, 8)).isZero();
        assertThat(MembershipQuotaResolver.exhausted(5, 5)).isTrue();
        assertThat(MembershipQuotaResolver.exhausted(5, 4)).isFalse();
    }

    @Test
    @DisplayName("upsellTier = hạng THẤP NHẤT có hạn mức cao hơn hạng hiện tại")
    void upsellTier_isLowestTierWithHigherLimit() {
        MembershipPlan silver = plan(MembershipTier.SILVER, "Bạc", 5, 10, 15, true, 10, true);
        MembershipPlan diamond = plan(MembershipTier.DIAMOND, "Kim cương", 20, 20, 60, true, 30, true);
        when(membershipPlanRepository.findAllByActiveTrueAndPurchasableTrueOrderBySortOrderAsc())
                .thenReturn(List.of(diamond, silver, goldPlan));

        // Đang FREE (2 lượt) -> hạng thấp nhất cao hơn là SILVER
        assertThat(resolver.findUpsellTier(MembershipTier.FREE, 2, MembershipPlan::getLiveTryonPerDay))
                .isEqualTo(MembershipTier.SILVER);

        // Đang GOLD (10 lượt) -> chỉ còn DIAMOND cao hơn
        assertThat(resolver.findUpsellTier(MembershipTier.GOLD, 10, MembershipPlan::getLiveTryonPerDay))
                .isEqualTo(MembershipTier.DIAMOND);

        // Đã ở hạng cao nhất -> null, mobile ẩn nút nâng cấp
        assertThat(resolver.findUpsellTier(MembershipTier.DIAMOND, 20, MembershipPlan::getLiveTryonPerDay))
                .isNull();
    }

    @Test
    @DisplayName("Đang không giới hạn -> không có gì để nâng cấp thêm, upsellTier = null")
    void unlimitedCurrentLimit_hasNoUpsell() {
        assertThat(resolver.findUpsellTier(MembershipTier.GOLD, null, MembershipPlan::getLiveTryonPerDay))
                .isNull();
    }

    // ================================================================ Helper

    private MemberSubscription subscription(MembershipPlan plan, Instant expiresAt) {
        return MemberSubscription.builder()
                .id(UUID.randomUUID())
                .member(Account.builder().id(accountId).build())
                .plan(plan)
                .tier(plan.getTier())
                .pricePaid(plan.getPricePerMonth())
                .liveTryonPerDay(plan.getLiveTryonPerDay())
                .liveTryonPerMonth(plan.getLiveTryonPerMonth())
                .liveTryonMaxSeconds(plan.getLiveTryonMaxSeconds())
                .aiTryonPerDay(plan.getAiTryonPerDay())
                .aiTryonPerMonth(plan.getAiTryonPerMonth())
                .featuredListing(plan.getFeaturedListing())
                .maxPendingConsignmentForms(plan.getMaxPendingConsignmentForms())
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(Instant.now().minus(20, ChronoUnit.DAYS))
                .expiresAt(expiresAt)
                .build();
    }

    private static MembershipPlan plan(MembershipTier tier, String name, int livePerDay, int maxSeconds,
                                        int aiPerDay, boolean featured, int maxForms, boolean purchasable) {
        return MembershipPlan.builder()
                .id(UUID.randomUUID())
                .tier(tier)
                .name(name)
                .pricePerMonth(BigDecimal.ZERO)
                .liveTryonPerDay(livePerDay)
                .liveTryonPerMonth(null)
                .liveTryonMaxSeconds(maxSeconds)
                .aiTryonPerDay(aiPerDay)
                .aiTryonPerMonth(null)
                .featuredListing(featured)
                .maxPendingConsignmentForms(maxForms)
                .purchasable(purchasable)
                .active(true)
                .sortOrder(tier.ordinal())
                .build();
    }
}
