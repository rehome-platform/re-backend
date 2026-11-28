package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class FundReconciliationResponse {

    public enum Status { BALANCED, MISMATCHED }

    private final Status status;
    private final BigDecimal difference;
    private final BigDecimal fundBalance;
    private final BigDecimal ledgerBalance;
    private final BigDecimal memberWalletBalance;
    private final BigDecimal memberLedgerBalance;
    private final BigDecimal memberDifference;
    private final BigDecimal ledgerNetTotal;
    private final long unmatchedEntries;
    private final long unbalancedPairs;
    private final Instant checkedAt;
}
