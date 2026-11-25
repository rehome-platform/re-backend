package com.rehome.rehome.component;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.TryOnImageRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiTryOnQuotaGuardTest {
    @Mock TryOnImageRepository images;
    @Mock MembershipQuotaResolver resolver;
    @Mock MemberProfileRepository profiles;
    @InjectMocks AiTryOnQuotaGuard guard;
    final UUID member = UUID.randomUUID();

    private void usage(long usedToday, long usedMonth, int bonus) {
        var quota = new MembershipQuotaResolver.EffectiveQuota(null, MembershipTier.FREE, "FREE",
                2, null, 10, 5, 20, false, 5, null, null);
        when(resolver.resolve(member)).thenReturn(quota);
        when(profiles.findBonusAiTryonCredits(member)).thenReturn(Optional.of(bonus));
        when(images.countByMember_IdAndCreatedAtBetween(eq(member), any(), any()))
                .thenReturn(usedToday, usedMonth);
    }

    @Test void availablePlanQuotaDoesNotRequireBonus() {
        usage(4, 19, 0);
        assertThat(guard.assertCanGenerate(member)).isFalse();
        verify(profiles, never()).consumeAiTryonCredit(any());
    }

    @Test void exhaustedDailyQuotaWithOneBonusUsesBonusWithoutConsumingBeforeSuccess() {
        usage(5, 19, 1);
        assertThat(guard.assertCanGenerate(member)).isTrue();
        verify(profiles, never()).consumeAiTryonCredit(any());
    }

    @Test void exhaustedDailyQuotaWithoutBonusIsRejected() {
        usage(5, 19, 0);
        assertThatThrownBy(() -> guard.assertCanGenerate(member)).isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRYON_IMAGE_QUOTA_EXCEEDED);
    }

    @Test void exhaustedMonthlyQuotaIsRejectedEvenWhenDailyQuotaRemains() {
        usage(1, 20, 0);
        assertThatThrownBy(() -> guard.assertCanGenerate(member)).isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRYON_IMAGE_QUOTA_EXCEEDED);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void bonusConsumptionReportsAtomicUpdateResult(int updated) {
        when(profiles.consumeAiTryonCredit(member)).thenReturn(updated);
        assertThat(guard.consumeBonusCredit(member)).isEqualTo(updated == 1);
        verify(profiles).consumeAiTryonCredit(member);
    }
}
