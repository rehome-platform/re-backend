package com.rehome.rehome.client;

import com.rehome.rehome.entity.marketplace.Warehouse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingFeeQuoteCacheTest {

    @Mock
    GhnShippingFeeCalculator calculator;

    private static Warehouse warehouse() {
        return Warehouse.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void sameRouteWithinTtl_callsGhnOnce() {
        Warehouse w = warehouse();
        when(calculator.calculate(w, "Phường A", "TP.HCM")).thenReturn(new BigDecimal("19500"));
        when(calculator.getFallbackFee()).thenReturn(new BigDecimal("25000"));
        ShippingFeeQuoteCache cache = new ShippingFeeQuoteCache(Duration.ofMinutes(5));

        assertThat(cache.calculate(calculator, w, "Phường A", "TP.HCM")).isEqualByComparingTo("19500");
        assertThat(cache.calculate(calculator, w, "Phường A", "TP.HCM")).isEqualByComparingTo("19500");

        verify(calculator, times(1)).calculate(w, "Phường A", "TP.HCM");
    }

    @Test
    void fallbackFee_isNeverCached() {
        Warehouse w = warehouse();
        when(calculator.calculate(w, "Phường A", "TP.HCM"))
                .thenReturn(new BigDecimal("25000"), new BigDecimal("19500"));
        when(calculator.getFallbackFee()).thenReturn(new BigDecimal("25000"));
        ShippingFeeQuoteCache cache = new ShippingFeeQuoteCache(Duration.ofMinutes(5));

        assertThat(cache.calculate(calculator, w, "Phường A", "TP.HCM")).isEqualByComparingTo("25000");
        assertThat(cache.calculate(calculator, w, "Phường A", "TP.HCM")).isEqualByComparingTo("19500");

        verify(calculator, times(2)).calculate(w, "Phường A", "TP.HCM");
    }

    @Test
    void expiredQuote_isRecalculated() {
        Warehouse w = warehouse();
        when(calculator.calculate(w, "Phường A", "TP.HCM"))
                .thenReturn(new BigDecimal("19500"), new BigDecimal("21000"));
        ShippingFeeQuoteCache cache = new ShippingFeeQuoteCache(Duration.ZERO);

        assertThat(cache.calculate(calculator, w, "Phường A", "TP.HCM")).isEqualByComparingTo("19500");
        assertThat(cache.calculate(calculator, w, "Phường A", "TP.HCM")).isEqualByComparingTo("21000");

        verify(calculator, times(2)).calculate(w, "Phường A", "TP.HCM");
    }

    @Test
    void warehouseWithoutId_isNeverCached() {
        Warehouse w = Warehouse.builder().build();
        when(calculator.calculate(w, "Phường A", "TP.HCM")).thenReturn(new BigDecimal("19500"));
        ShippingFeeQuoteCache cache = new ShippingFeeQuoteCache(Duration.ofMinutes(5));

        cache.calculate(calculator, w, "Phường A", "TP.HCM");
        cache.calculate(calculator, w, "Phường A", "TP.HCM");

        verify(calculator, times(2)).calculate(w, "Phường A", "TP.HCM");
    }
}
