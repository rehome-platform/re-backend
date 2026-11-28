package com.rehome.rehome.client;

import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Warehouse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingFeeMemoTest {

    @Mock
    GhnShippingFeeCalculator calculator;

    private static Warehouse warehouse() {
        return Warehouse.builder().id(UUID.randomUUID()).build();
    }

    private static ShippingAddress address(String wardName, String detail) {
        return ShippingAddress.builder().addressDetail(detail).wardName(wardName).provinceName("TP.HCM").build();
    }

    @Test
    void sameRoute_callsGhnOnce_andReusesTheFee() {
        Warehouse w = warehouse();
        ShippingAddress a = address("Phường A", "1 Lê Lợi");
        when(calculator.calculate(w, a)).thenReturn(new BigDecimal("19500"));
        when(calculator.getFallbackFee()).thenReturn(new BigDecimal("25000"));
        ShippingFeeMemo memo = new ShippingFeeMemo(calculator);

        assertThat(memo.calculate(w, a)).isEqualByComparingTo("19500");
        assertThat(memo.calculate(w, a)).isEqualByComparingTo("19500");

        verify(calculator, times(1)).calculate(w, a);
    }

    @Test
    void fallbackFee_isNotRemembered_soTheNextItemAsksGhnAgain() {
        Warehouse w = warehouse();
        ShippingAddress a = address("Phường A", "1 Lê Lợi");
        when(calculator.calculate(w, a)).thenReturn(new BigDecimal("25000"), new BigDecimal("19500"));
        when(calculator.getFallbackFee()).thenReturn(new BigDecimal("25000"));
        ShippingFeeMemo memo = new ShippingFeeMemo(calculator);

        assertThat(memo.calculate(w, a)).isEqualByComparingTo("25000");
        assertThat(memo.calculate(w, a)).isEqualByComparingTo("19500");

        verify(calculator, times(2)).calculate(w, a);
    }

    @Test
    void differentWarehouseWardOrStreet_isCalculatedSeparately() {
        Warehouse w1 = warehouse();
        Warehouse w2 = warehouse();
        when(calculator.calculate(any(), any(ShippingAddress.class))).thenReturn(new BigDecimal("19500"));
        ShippingFeeMemo memo = new ShippingFeeMemo(calculator);

        memo.calculate(w1, address("Phường A", "1 Lê Lợi"));
        memo.calculate(w2, address("Phường A", "1 Lê Lợi"));
        memo.calculate(w1, address("Phường B", "1 Lê Lợi"));
        memo.calculate(w1, address("Phường A", "99 Hai Bà Trưng"));

        verify(calculator, times(4)).calculate(any(), any(ShippingAddress.class));
    }

    @Test
    void warehouseWithoutId_isNeverRemembered() {
        Warehouse w = Warehouse.builder().build();
        ShippingAddress a = address("Phường A", "1 Lê Lợi");
        when(calculator.calculate(w, a)).thenReturn(new BigDecimal("19500"));
        ShippingFeeMemo memo = new ShippingFeeMemo(calculator);

        memo.calculate(w, a);
        memo.calculate(w, a);

        verify(calculator, times(2)).calculate(w, a);
    }
}
