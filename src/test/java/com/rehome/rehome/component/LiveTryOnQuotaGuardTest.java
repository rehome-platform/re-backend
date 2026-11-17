package com.rehome.rehome.component;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.LiveTryOnStatus;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.tryon.LiveTryOnSession;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.LiveTryOnSessionRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveTryOnQuotaGuardTest {
    @Test
    void firstFrameConsumesBonusCreditOnceWhenPlanQuotaIsExhausted() {
        var sessions = mock(LiveTryOnSessionRepository.class);
        var resolver = mock(MembershipQuotaResolver.class);
        var profiles = mock(MemberProfileRepository.class);
        var accounts = mock(AccountRepository.class);
        var guard = new LiveTryOnQuotaGuard(sessions, resolver, profiles, accounts);
        Account account = Account.builder().id(UUID.randomUUID()).build();
        UUID sessionId = UUID.randomUUID();
        LiveTryOnSession session = LiveTryOnSession.builder()
                .id(sessionId).member(account).status(LiveTryOnStatus.STARTED).build();
        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        when(sessions.findByIdAndMember_Id(sessionId, account.getId())).thenReturn(Optional.of(session));
        when(resolver.resolve(account.getId())).thenReturn(new MembershipQuotaResolver.EffectiveQuota(
                null, MembershipTier.FREE, "Free", 0, null, 10, 0, null, false, 0, null, null));
        when(profiles.consumeLiveTryonCredit(account.getId())).thenReturn(1);

        guard.confirmFirstFrame(account, sessionId);
        var firstFrameAt = session.getFirstFrameAt();
        guard.confirmFirstFrame(account, sessionId);

        assertThat(firstFrameAt).isNotNull();
        assertThat(session.getFirstFrameAt()).isEqualTo(firstFrameAt);
        verify(profiles, times(1)).consumeLiveTryonCredit(account.getId());
    }
}
