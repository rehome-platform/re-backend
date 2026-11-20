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
    Map<String, String> stored = new HashMap<>();

    @BeforeEach void setup() {
        cache = new ProductListCache(redis, new ObjectMapper(), true, "30s");
        lenient().when(redis.opsForValue()).thenReturn(values);
        lenient().when(values.get(anyString())).thenAnswer(inv -> stored.get(inv.getArgument(0)));
        lenient().doAnswer(inv -> { stored.put(inv.getArgument(0), inv.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), any(Duration.class));
        lenient().doAnswer(inv -> { stored.put(inv.getArgument(0), inv.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString());
    }

    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    ProductListCache.Key key(UUID viewer) {
        return new ProductListCache.Key(null, null, null, null, null, null, null, 0, 20, viewer);
    }

    PageResponse<ProductSummaryResponse> page(boolean mine) {
        return new PageResponse<>(List.of(ProductSummaryResponse.builder().id("SKU-1")
                .currentPrice(new BigDecimal("150000.00")).isMine(mine).featured(true).build()), 0, 20, 1, 1);
    }

    @Test void repeatedRequestUsesTypedJsonCacheAndPreservesResponse() {
        AtomicInteger calls = new AtomicInteger();
        var request = key(UUID.randomUUID());
        var expected = page(true);
        cache.getOrLoad(request, () -> { calls.incrementAndGet(); return expected; });
        var cached = cache.getOrLoad(request, () -> { throw new AssertionError("Cache should hit"); });
        assertThat(cached).usingRecursiveComparison().isEqualTo(expected);
        assertThat(calls.get()).isEqualTo(1);
        verify(values).set(startsWith("rehome:products:v1:"), anyString(), eq(Duration.ofSeconds(30)));
    }

    @Test void everyFilterPaginationAndViewerHasAnIndependentKey() {
        List<ProductListCache.Key> requests = new ArrayList<>();
        requests.add(key(null));
        requests.add(key(UUID.randomUUID()));
        requests.add(new ProductListCache.Key("shirt", null, null, null, null, null, null, 0, 20, null));
        requests.add(new ProductListCache.Key(null, UUID.randomUUID(), null, null, null, null, null, 0, 20, null));
        requests.add(new ProductListCache.Key(null, null, UUID.randomUUID(), null, null, null, null, 0, 20, null));
        requests.add(new ProductListCache.Key(null, null, null, BigDecimal.ONE, null, null, null, 0, 20, null));
        requests.add(new ProductListCache.Key(null, null, null, null, BigDecimal.TEN, null, null, 0, 20, null));
        requests.add(new ProductListCache.Key(null, null, null, null, null, UUID.randomUUID(), null, 0, 20, null));
        requests.add(new ProductListCache.Key(null, null, null, null, null, null, TryOnSlot.TOP, 0, 20, null));
        requests.add(new ProductListCache.Key(null, null, null, null, null, null, null, 1, 20, null));
        requests.add(new ProductListCache.Key(null, null, null, null, null, null, null, 0, 10, null));
        requests.forEach(request -> cache.getOrLoad(request, () -> page(request.viewerId() != null)));
        assertThat(stored).hasSize(requests.size());
        var anonymous = cache.getOrLoad(key(null), () -> { throw new AssertionError(); });
        assertThat(anonymous.getContent().get(0).getIsMine()).isFalse();
    }

    @Test void failedOrUnavailableRedisDoesNotBreakDatabaseResponse() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        AtomicInteger loads = new AtomicInteger();
        assertThat(cache.getOrLoad(key(null), () -> { loads.incrementAndGet(); return page(false); }))
                .isEqualTo(page(false));
        assertThat(loads.get()).isEqualTo(1);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test void corruptJsonAndFailedCacheWriteAreNonFatal() {
        cache.getOrLoad(key(null), () -> page(false));
        stored.replaceAll((k, v) -> "not-json");
        assertThat(cache.getOrLoad(key(null), () -> page(true))).isEqualTo(page(true));
        stored.clear();
        doThrow(new RedisConnectionFailureException("offline")).when(values)
                .set(anyString(), anyString(), any(Duration.class));
        assertThat(cache.getOrLoad(key(null), () -> page(false))).isEqualTo(page(false));
    }

    @Test void applicationFailureIsNotCachedOrRetried() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> cache.getOrLoad(key(null), () -> {
            calls.incrementAndGet(); throw new IllegalStateException("database");
        })).hasMessage("database");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(stored).isEmpty();
    }

    @Test void rollbackKeepsCacheAndCommitInvalidatesOnlyOnce() {
        cache.getOrLoad(key(null), () -> page(false));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        cache.invalidateAfterCommit();
        TransactionSynchronizationManager.getSynchronizations().forEach(s ->
                s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
        assertThat(cache.getOrLoad(key(null), () -> { throw new AssertionError(); })).isEqualTo(page(false));

        TransactionSynchronizationManager.initSynchronization();
        cache.invalidateAfterCommit();
        cache.invalidateAfterCommit();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(cache.getOrLoad(key(null), () -> page(true))).isEqualTo(page(true));
        verify(values, times(1)).set(eq("rehome:products:v1:generation"), anyString());
    }

    @Test void olderInFlightReadCannotRepopulateCurrentGeneration() {
        cache.getOrLoad(key(null), () -> { cache.invalidateAfterCommit(); return page(false); });
        assertThat(cache.getOrLoad(key(null), () -> page(true))).isEqualTo(page(true));
    }

    @Test void disabledCacheDoesNotUseRedisAndInvalidationFailureIsNonFatal() {
        var disabled = new ProductListCache(redis, new ObjectMapper(), false, "30s");
        disabled.getOrLoad(key(null), () -> page(false));
        disabled.invalidateAfterCommit();
        verifyNoInteractions(redis);
        doThrow(new RedisConnectionFailureException("offline")).when(values).set(anyString(), anyString());
        assertThatCode(cache::invalidateAfterCommit).doesNotThrowAnyException();
    }
}
