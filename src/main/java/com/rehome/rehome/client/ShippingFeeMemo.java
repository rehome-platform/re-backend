package com.rehome.rehome.client;

import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Warehouse;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ShippingFeeMemo {

    private final GhnShippingFeeCalculator calculator;
    private final Map<Route, BigDecimal> feeByRoute = new HashMap<>();

    public ShippingFeeMemo(GhnShippingFeeCalculator calculator) {
        this.calculator = calculator;
    }

    public BigDecimal calculate(Warehouse fromWarehouse, ShippingAddress toAddress) {
        if (fromWarehouse == null || fromWarehouse.getId() == null || toAddress == null) {
            return calculator.calculate(fromWarehouse, toAddress);
        }
        Route route = new Route(fromWarehouse.getId(), toAddress.getWardName(), toAddress.getProvinceName(),
                toAddress.getWardCode(), toAddress.getAddressDetail());
        BigDecimal known = feeByRoute.get(route);
        if (known != null) {
            return known;
        }
        BigDecimal fee = calculator.calculate(fromWarehouse, toAddress);
        if (fee != null && !isFallback(fee)) {
            feeByRoute.put(route, fee);
        }
        return fee;
    }

    private boolean isFallback(BigDecimal fee) {
        BigDecimal fallback = calculator.getFallbackFee();
        return fallback != null && fee.compareTo(fallback) == 0;
    }

    private record Route(UUID warehouseId, String toWardName, String toProvinceName,
                         String toWardCode, String toAddressDetail) {
    }
}
