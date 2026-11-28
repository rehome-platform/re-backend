package com.rehome.rehome.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ProductSummaryResponse;
import com.rehome.rehome.entity.enums.TryOnSlot;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductListCacheTest {
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;
    ProductListCache cache;
    Map<String, String> stored = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach void setup() {
        cache = new ProductListCache(redis, new ObjectMapper(), true, "30s");
        lenient().when(redis.opsForValue()).thenReturn(values);
        lenient().when(values.get(anyString())).thenAnswer(inv -> stored.get(inv.getArgument(0)));
        lenient().doAnswer(inv -> { stored.put(inv.getArgument(0), inv.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), any(Duration.class));
        lenient().doAnswer(inv -> { stored.put(inv.getArgument(0), inv.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString());
    }

    static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test void equivalentSearchAndPricesShareKey() {
        var first = new ProductListCache.Key(" ÁO ", null, null, new BigDecimal("150000.00"), null,
                null, null, 0, 20);
        var same = new ProductListCache.Key("ao", null, null, new BigDecimal("150000"), null,
                null, null, 0, 20);
        assertThat(first).isEqualTo(same);
        assertThat(first).isNotEqualTo(new ProductListCache.Key("ao", null, null,
                new BigDecimal("150000"), null, null, null, 1, 20));
    }

    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    ProductListCache.Key key() {
        return new ProductListCache.Key(null, null, null, null, null, null, null, 0, 20);
    }

    PageResponse<ProductListCache.Item> page(boolean variant) {
        return new PageResponse<>(List.of(new ProductListCache.Item(ProductSummaryResponse.builder()
                .id(variant ? "SKU-2" : "SKU-1").currentPrice(new BigDecimal("150000.00")).featured(true).build(),
                OWNER)), 0, 20, 1, 1);
    }

    @Test void repeatedRequestUsesTypedJsonCacheAndPreservesResponse() {
        AtomicInteger calls = new AtomicInteger();
        var request = key();
        var expected = page(true);
        cache.getOrLoad(request, () -> { calls.incrementAndGet(); return expected; });
        var cached = cache.getOrLoad(request, () -> { throw new AssertionError("Cache should hit"); });
        assertThat(cached).usingRecursiveComparison().isEqualTo(expected);
        assertThat(cached.getContent().get(0).memberId()).isEqualTo(OWNER);
        assertThat(calls.get()).isEqualTo(1);
        verify(values).set(startsWith("rehome:products:v2:"), anyString(), eq(Duration.ofSeconds(30)));
    }

    @Test void everyFilterAndPaginationHasAnIndependentKey() {
        List<ProductListCache.Key> requests = new ArrayList<>();
        requests.add(key());
        requests.add(new ProductListCache.Key("shirt", null, null, null, null, null, null, 0, 20));
        requests.add(new ProductListCache.Key(null, UUID.randomUUID(), null, null, null, null, null, 0, 20));
        requests.add(new ProductListCache.Key(null, null, UUID.randomUUID(), null, null, null, null, 0, 20));
        requests.add(new ProductListCache.Key(null, null, null, BigDecimal.ONE, null, null, null, 0, 20));
        requests.add(new ProductListCache.Key(null, null, null, null, BigDecimal.TEN, null, null, 0, 20));
        requests.add(new ProductListCache.Key(null, null, null, null, null, UUID.randomUUID(), null, 0, 20));
        requests.add(new ProductListCache.Key(null, null, null, null, null, null, TryOnSlot.TOP, 0, 20));
        requests.add(new ProductListCache.Key(null, null, null, null, null, null, null, 1, 20));
        requests.add(new ProductListCache.Key(null, null, null, null, null, null, null, 0, 10));
        requests.forEach(request -> cache.getOrLoad(request, () -> page(false)));
        assertThat(stored).hasSize(requests.size());
        assertThat(cache.getOrLoad(key(), () -> { throw new AssertionError(); })).isEqualTo(page(false));
    }

    @Test void failedOrUnavailableRedisDoesNotBreakDatabaseResponse() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        AtomicInteger loads = new AtomicInteger();
        assertThat(cache.getOrLoad(key(), () -> { loads.incrementAndGet(); return page(false); }))
                .isEqualTo(page(false));
        assertThat(loads.get()).isEqualTo(1);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test void corruptJsonAndFailedCacheWriteAreNonFatal() {
        cache.getOrLoad(key(), () -> page(false));
        stored.replaceAll((k, v) -> "not-json");
        assertThat(cache.getOrLoad(key(), () -> page(true))).isEqualTo(page(true));
        stored.clear();
        doThrow(new RedisConnectionFailureException("offline")).when(values)
                .set(anyString(), anyString(), any(Duration.class));
        assertThat(cache.getOrLoad(key(), () -> page(false))).isEqualTo(page(false));
    }

    @Test void applicationFailureIsNotCachedOrRetried() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> cache.getOrLoad(key(), () -> {
            calls.incrementAndGet(); throw new IllegalStateException("database");
        })).hasMessage("database");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(stored).isEmpty();
    }

    @Test void rollbackKeepsCacheAndCommitInvalidatesOnlyOnce() {
        cache.getOrLoad(key(), () -> page(false));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        cache.invalidateAfterCommit();
        TransactionSynchronizationManager.getSynchronizations().forEach(s ->
                s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
        assertThat(cache.getOrLoad(key(), () -> { throw new AssertionError(); })).isEqualTo(page(false));

        TransactionSynchronizationManager.initSynchronization();
        cache.invalidateAfterCommit();
        cache.invalidateAfterCommit();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(cache.getOrLoad(key(), () -> page(true))).isEqualTo(page(true));
        verify(values, times(1)).set(eq("rehome:products:v1:generation"), anyString());
    }

    @Test void olderInFlightReadCannotRepopulateCurrentGeneration() {
        cache.getOrLoad(key(), () -> { cache.invalidateAfterCommit(); return page(false); });
        assertThat(cache.getOrLoad(key(), () -> page(true))).isEqualTo(page(true));
    }

    @Test void disabledCacheDoesNotUseRedisAndInvalidationFailureIsNonFatal() {
        var disabled = new ProductListCache(redis, new ObjectMapper(), false, "30s");
        disabled.getOrLoad(key(), () -> page(false));
        disabled.invalidateAfterCommit();
        verifyNoInteractions(redis);
        doThrow(new RedisConnectionFailureException("offline")).when(values).set(anyString(), anyString());
        assertThatCode(cache::invalidateAfterCommit).doesNotThrowAnyException();
    }

    @Test void concurrentSamePageCoalescesEvenWhenRedisIsOffline() throws Exception {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var followerThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        AtomicInteger calls = new AtomicInteger();
        try {
            var leader = executor.submit(() -> cache.getOrLoad(key(), () -> {
                calls.incrementAndGet(); entered.countDown();
                try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("timeout"); }
                catch (InterruptedException ex) { throw new RuntimeException(ex); }
                return page(false);
            }));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var follower = executor.submit(() -> {
                followerThread.set(Thread.currentThread());
                return cache.getOrLoad(key(), () -> { calls.incrementAndGet(); return page(true); });
            });
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3)).until(() ->
                    followerThread.get() != null && followerThread.get().getState() == Thread.State.WAITING);
            release.countDown();
            assertThat(follower.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isSameAs(leader.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(calls.get()).isEqualTo(1);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void requestAfterCommitDoesNotJoinAnOlderInflightLoad() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var old = executor.submit(() -> cache.getOrLoad(key(), () -> {
                entered.countDown();
                try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("timeout"); }
                catch (InterruptedException ex) { throw new RuntimeException(ex); }
                return page(false);
            }));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            cache.invalidateAfterCommit();
            var fresh = executor.submit(() -> cache.getOrLoad(key(), () -> page(true)));
            assertThat(fresh.get(3, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(page(true));
            release.countDown();
            old.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void sharedFailureKeepsOriginalExceptionAndNextRequestCanRetry() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var followerThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var failure = new com.rehome.rehome.exception.AppException(com.rehome.rehome.exception.ErrorCode.PRODUCT_NOT_FOUND);
        try {
            var leader = executor.submit(() -> cache.getOrLoad(key(), () -> {
                entered.countDown();
                try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("timeout"); }
                catch (InterruptedException ex) { throw new RuntimeException(ex); }
                throw failure;
            }));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var follower = executor.submit(() -> {
                followerThread.set(Thread.currentThread());
                return cache.getOrLoad(key(), () -> { throw new AssertionError("Duplicate database load"); });
            });
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3)).until(() ->
                    followerThread.get() != null && followerThread.get().getState() == Thread.State.WAITING);
            release.countDown();
            assertThatThrownBy(() -> leader.get(5, java.util.concurrent.TimeUnit.SECONDS)).hasCause(failure);
            assertThatThrownBy(() -> follower.get(5, java.util.concurrent.TimeUnit.SECONDS)).hasCause(failure);
            assertThat(cache.getOrLoad(key(), () -> page(false))).isEqualTo(page(false));
        } finally { release.countDown(); executor.shutdownNow(); }
    }
}
