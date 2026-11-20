package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.RefreshToken;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.RefreshTokenService;
import com.rehome.rehome.serviceImpl.RefreshTokenServiceImpl;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import(RefreshTokenServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshTokenRotationTest {
    @Autowired RefreshTokenRepository tokens;
    @Autowired AccountRepository accounts;
    @Autowired RefreshTokenService service;
    @Autowired PlatformTransactionManager manager;
    TransactionTemplate transaction;
    Account account;

    @BeforeEach void setup() {
        transaction = new TransactionTemplate(manager);
        transaction.setTimeout(10);
        account = transaction.execute(s -> accounts.save(Account.builder()
                .email(UUID.randomUUID() + "@refresh.test").passwordHash("test")
                .role(Role.MEMBER).status(AccountStatus.ACTIVE).emailVerified(true).build()));
    }

    String create() {
        return transaction.execute(s -> service.createRefreshToken(account).getToken());
    }

    String rotate(String old) {
        return transaction.execute(s -> service.rotateRefreshToken(service.findByTokenForUpdate(old)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND))).getToken());
    }

    @Test void rotationPreservesAnotherDeviceAndAllowsTheNextRefresh() {
        String first = create();
        String second = create();
        Long rowId = tokens.findByToken(first).orElseThrow().getId();
        String replacement = rotate(first);
        assertThat(tokens.findByToken(first)).isEmpty();
        assertThat(tokens.findByToken(replacement).orElseThrow().getId()).isEqualTo(rowId);
        assertThat(tokens.findByToken(second)).isPresent();
        assertThat(tokens.findByToken(second).orElseThrow().isRevoked()).isFalse();
        String next = rotate(replacement);
        assertThat(tokens.findByToken(replacement)).isEmpty();
        assertThat(tokens.findByToken(next)).isPresent();
        assertThat(tokens.findByToken(second)).isPresent();
    }

    @Test void rollbackDoesNotDestroyTheOriginalRefreshToken() {
        String old = create();
        assertThatThrownBy(() -> transaction.execute(s -> {
            RefreshToken token = service.findByTokenForUpdate(old).orElseThrow();
            service.rotateRefreshToken(token);
            tokens.flush();
            throw new IllegalStateException("Simulated failure after rotation");
        })).hasMessage("Simulated failure after rotation");
        assertThat(tokens.findByToken(old)).isPresent();
    }

    @Test void concurrentRequestsCannotRotateTheSameOldTokenTwice() throws Exception {
        String old = create();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> transaction.execute(s -> {
                RefreshToken token = service.findByTokenForUpdate(old).orElseThrow();
                locked.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return service.rotateRefreshToken(token).getToken();
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> { secondStarted.countDown(); return rotate(old); });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            String replacement = first.get(5, TimeUnit.SECONDS);
            ExecutionException error = assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
            assertThat(error.getCause()).isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_FOUND);
            assertThat(tokens.findByToken(replacement)).isPresent();
            assertThat(tokens.findByToken(old)).isEmpty();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test void expiredOrRevokedTokenCannotBeRotated() {
        String old = transaction.execute(s -> tokens.save(RefreshToken.builder().account(account)
                .token(UUID.randomUUID().toString()).expiryDate(Instant.now().minusSeconds(1)).build()).getToken());
        assertThatThrownBy(() -> rotate(old)).hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_EXPIRED);
        transaction.executeWithoutResult(s -> {
            RefreshToken token = tokens.findByTokenForUpdate(old).orElseThrow();
            token.setExpiryDate(Instant.now().plusSeconds(3600));
            token.setRevoked(true);
            tokens.save(token);
        });
        assertThatThrownBy(() -> rotate(old)).hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_REVOKED);
    }
}
