package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.repository.SystemConfigRepository;
import com.rehome.rehome.service.SystemConfigReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class SystemConfigReaderImpl implements SystemConfigReader {

    private static final long CACHE_TTL_NANOS = Duration.ofSeconds(60).toNanos();

    private final SystemConfigRepository systemConfigRepository;
    private final Map<String, CachedConfig> cache = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        CachedConfig config = read(key);
        if (!config.present()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(config.value().trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("system_configs[" + key + "] không phải số nguyên hợp lệ: " + config.value(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        CachedConfig config = read(key);
        if (!config.present()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(config.value().trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("system_configs[" + key + "] không phải số hợp lệ: " + config.value(), e);
        }
    }

    @Override
    public void evict(String key) {
        cache.remove(key);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    cache.remove(key);
                }
            });
        }
    }

    private CachedConfig read(String key) {
        long now = System.nanoTime();
        CachedConfig cached = cache.get(key);
        if (cached != null && now - cached.loadedAtNanos() < CACHE_TTL_NANOS) {
            return cached;
        }
        CachedConfig loaded = systemConfigRepository.findByKey(key)
                .map(c -> new CachedConfig(true, c.getValue(), now))
                .orElseGet(() -> new CachedConfig(false, null, now));
        cache.put(key, loaded);
        return loaded;
    }

    private record CachedConfig(boolean present, String value, long loadedAtNanos) {
    }
}
