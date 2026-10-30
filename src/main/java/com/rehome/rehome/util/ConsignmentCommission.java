package com.rehome.rehome.util;

import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ConsignmentCommission {
    private ConsignmentCommission() {}

    public static BigDecimal rateForTerm(ConsignmentReceipt r, int term) {
        return switch (term) {
            case 1 -> r.getCommissionRateTerm1();
            case 2 -> r.getCommissionRateTerm2();
            default -> r.getCommissionRateTerm3();
        };
    }

    public static BigDecimal payout(BigDecimal price, BigDecimal rate) {
        return price.multiply(BigDecimal.ONE.subtract(rate)).setScale(0, RoundingMode.HALF_UP);
    }
}
