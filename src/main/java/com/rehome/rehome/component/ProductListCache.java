package com.rehome.rehome.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ProductSummaryResponse;
import com.rehome.rehome.entity.enums.TryOnSlot;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
public class ProductListCache {
    private static final String GENERATION_KEY = "rehome:products:v1:generation";
    private static final String PAGE_PREFIX = "rehome:products:v2:";
    private static final TypeReference<PageResponse<Item>> PAGE_TYPE = new TypeReference<>() {};
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final Duration ttl;
    private final ConcurrentHashMap<String, CompletableFuture<PageResponse<Item>>> inFlight = new ConcurrentHashMap<>();
    private final AtomicLong localGeneration = new AtomicLong();

    public record Item(ProductSummaryResponse summary, UUID memberId) {
    }

    public record Key(String search, UUID categoryId, UUID brandId, BigDecimal priceMin,
                      BigDecimal priceMax, UUID warehouseId, TryOnSlot tryonSlot,
                      int page, int size) {
        public Key {
            search = com.rehome.rehome.util.VietnameseText.normalize(search);
            if (search != null && search.isBlank()) search = null;
            priceMin = priceMin == null ? null : priceMin.stripTrailingZeros();
            priceMax = priceMax == null ? null : priceMax.stripTrailingZeros();
        }
    }

    public ProductListCache(StringRedisTemplate redis, ObjectMapper mapper,
                            @Value("${app.cache.products.enabled:true}") boolean enabled,
                            @Value("${app.cache.products.ttl:30s}") String ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.enabled = enabled;
        this.ttl = DurationStyle.detectAndParse(ttl);
    }

    public PageResponse<Item> getOrLoad(Key request, Supplier<PageResponse<Item>> loader) {
        if (!enabled || ttl.isZero() || ttl.isNegative()) return loader.get();
        String key = null;
        String digest;
        long epoch = localGeneration.get();
        try {
            digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return loader.get();
        }
        try {
            String generation = redis.opsForValue().get(GENERATION_KEY);
            key = PAGE_PREFIX + (generation == null ? "0" : generation) + ":" + digest;
        } catch (Exception ex) {
            key = null;
            log.warn("Product cache read failed; loading database ({})", ex.getClass().getSimpleName());
        }

        String flightKey = epoch + ":" + (key == null ? "offline:" + digest : key);
        var pending = new CompletableFuture<PageResponse<Item>>();
        var existing = inFlight.putIfAbsent(flightKey, pending);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException ex) {
                if (ex.getCause() instanceof RuntimeException failure) throw failure;
                if (ex.getCause() instanceof Error failure) throw failure;
                throw ex;
            }
        }
        try {
            var result = readPageOrLoad(key, loader);
            pending.complete(result);
            return result;
        } catch (RuntimeException | Error ex) {
            pending.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlight.remove(flightKey, pending);
        }
    }

    private PageResponse<Item> readPageOrLoad(String key, Supplier<PageResponse<Item>> loader) {
        if (key != null) {
            try {
                String cached = redis.opsForValue().get(key);
                if (cached != null) return mapper.readValue(cached, PAGE_TYPE);
            } catch (Exception ex) {
                key = null;
                log.warn("Product cache page read failed; loading database ({})", ex.getClass().getSimpleName());
            }
        }

        PageResponse<Item> result = loader.get();
        if (key != null) {
            try {
                redis.opsForValue().set(key, mapper.writeValueAsString(result), ttl);
            } catch (Exception ex) {
                log.warn("Product cache write failed ({})", ex.getClass().getSimpleName());
            }
        }
        return result;
    }

    public void invalidateAfterCommit() {
        if (!enabled || ttl.isZero() || ttl.isNegative()) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            if (TransactionSynchronizationManager.getSynchronizations().stream()
                    .noneMatch(Invalidation.class::isInstance)) {
                TransactionSynchronizationManager.registerSynchronization(new Invalidation());
            }
        } else {
            invalidate();
        }
    }

    private final class Invalidation implements TransactionSynchronization {
        @Override public void afterCommit() { invalidate(); }
    }

    private void invalidate() {
        localGeneration.incrementAndGet();
        try {
            redis.opsForValue().set(GENERATION_KEY, UUID.randomUUID().toString());
        } catch (Exception ex) {
            log.warn("Product cache invalidation failed; existing pages expire by TTL ({})",
                    ex.getClass().getSimpleName());
        }
    }
}
