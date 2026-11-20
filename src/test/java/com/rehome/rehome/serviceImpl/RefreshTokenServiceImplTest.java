package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.RefreshToken;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock Account account;
    @InjectMocks RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshTokenExpirationMs", 60_000L);
    }

    @Test
    void createRefreshToken_buildsAndSavesToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        RefreshToken result = service.createRefreshToken(account);
        Instant after = Instant.now();

        assertThat(result.getAccount()).isSameAs(account);
        assertThat(UUID.fromString(result.getToken())).isNotNull();
        assertThat(result.isRevoked()).isFalse();
        assertThat(result.getExpiryDate())
                .isBetween(before.plusMillis(60_000), after.plusMillis(60_000));
        verify(refreshTokenRepository).save(result);
    }

    @Test
    void findByToken_delegatesToRepository() {
        RefreshToken token = RefreshToken.builder().token("t").build();
        when(refreshTokenRepository.findByToken("t")).thenReturn(Optional.of(token));

        assertThat(service.findByToken("t")).containsSame(token);
    }

    @Test
    void verifyExpiration_revoked_throws() {
        RefreshToken token = RefreshToken.builder()
                .token("t").revoked(true).expiryDate(Instant.now().plusSeconds(3600)).build();

        assertThatThrownBy(() -> service.verifyExpiration(token))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_REVOKED);

        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void verifyExpiration_expired_throwsStandardAuthErrorWithoutChangingData() {
        RefreshToken token = RefreshToken.builder()
                .token("t").revoked(false).expiryDate(Instant.now().minusSeconds(10)).build();

        assertThatThrownBy(() -> service.verifyExpiration(token))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_EXPIRED);

        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test void rotateRefreshTokenUpdatesOnlyTheLockedRow() {
        RefreshToken token = RefreshToken.builder().id(5L).account(account).token("old")
                .expiryDate(Instant.now().plusSeconds(3600)).build();
        when(refreshTokenRepository.save(token)).thenAnswer(inv -> inv.getArgument(0));
        Instant before = Instant.now();
        RefreshToken rotated = service.rotateRefreshToken(token);
        assertThat(rotated.getId()).isEqualTo(5L);
        assertThat(rotated.getAccount()).isSameAs(account);
        assertThat(rotated.getToken()).isNotEqualTo("old");
        assertThat(UUID.fromString(rotated.getToken())).isNotNull();
        assertThat(rotated.getExpiryDate()).isAfterOrEqualTo(before.plusSeconds(60));
        verify(refreshTokenRepository).save(token);
        verify(refreshTokenRepository, never()).deleteByAccount(any());
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test void revokedTokenCannotBeRotatedBackIntoAValidSession() {
        RefreshToken token = RefreshToken.builder().token("old").revoked(true)
                .expiryDate(Instant.now().plusSeconds(3600)).build();
        assertThatThrownBy(() -> service.rotateRefreshToken(token))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_REVOKED);
        verifyNoInteractions(refreshTokenRepository);
        assertThat(token.getToken()).isEqualTo("old");
    }

    @Test void lockedLookupDelegatesToLockingRepositoryQuery() {
        RefreshToken token = RefreshToken.builder().token("locked").build();
        when(refreshTokenRepository.findByTokenForUpdate("locked")).thenReturn(Optional.of(token));
        assertThat(service.findByTokenForUpdate("locked")).containsSame(token);
    }

    @Test
    void verifyExpiration_valid_returnsSameToken() {
        RefreshToken token = RefreshToken.builder()
                .token("t").revoked(false).expiryDate(Instant.now().plusSeconds(3600)).build();

        assertThat(service.verifyExpiration(token)).isSameAs(token);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void revokeAllAccountTokens_delegatesToRepository() {
        service.revokeAllAccountTokens(account);

        verify(refreshTokenRepository).revokeAllByAccount(account);
    }

    @Test
    void deleteByAccount_delegatesToRepository() {
        service.deleteByAccount(account);

        verify(refreshTokenRepository).deleteByAccount(account);
    }
}
