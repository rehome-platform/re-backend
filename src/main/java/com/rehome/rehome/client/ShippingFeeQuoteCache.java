package com.rehome.rehome.client;

import com.rehome.rehome.entity.marketplace.Warehouse;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShippingFeeQuoteCache {

    private static final int MAX_ENTRIES = 10_000;

    private final long ttlNanos;
    private final Map<Route, Quote> quotes = new ConcurrentHashMap<>();

    public ShippingFeeQuoteCache(Duration ttl) {
        this.ttlNanos = ttl.toNanos();
    }

    public BigDecimal calculate(GhnShippingFeeCalculator calculator, Warehouse fromWarehouse,
                                String toWardName, String toProvinceName) {
        if (fromWarehouse == null || fromWarehouse.getId() == null) {
            return calculator.calculate(fromWarehouse, toWardName, toProvinceName);
        }
        Route route = new Route(fromWarehouse.getId(), toWardName, toProvinceName);
        long now = System.nanoTime();
        Quote quote = quotes.get(route);
        if (quote != null && now - quote.quotedAtNanos() < ttlNanos) {
            return quote.fee();
        }
        BigDecimal fee = calculator.calculate(fromWarehouse, toWardName, toProvinceName);
        if (fee != null && !isFallback(calculator, fee)) {
            if (quotes.size() >= MAX_ENTRIES) {
                quotes.values().removeIf(q -> now - q.quotedAtNanos() >= ttlNanos);
                if (quotes.size() >= MAX_ENTRIES) {
                    quotes.clear();
                }
            }
            quotes.put(route, new Quote(fee, now));
        }
        return fee;
    }

    private static boolean isFallback(GhnShippingFeeCalculator calculator, BigDecimal fee) {
        BigDecimal fallback = calculator.getFallbackFee();
        return fallback != null && fee.compareTo(fallback) == 0;
    }

    private record Route(UUID warehouseId, String toWardName, String toProvinceName) {
    }

    private record Quote(BigDecimal fee, long quotedAtNanos) {
    }
}
