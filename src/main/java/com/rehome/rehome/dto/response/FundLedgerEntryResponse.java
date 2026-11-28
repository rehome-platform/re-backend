package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class FundLedgerEntryResponse {
    private final UUID id;
    private final String transactionNo;
    private final WalletTransactionType type;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final TransactionStatus status;
    private final String note;
    private final Instant createdAt;

    private final RelatedAccount relatedAccount;
    private final ReferenceTransaction referenceTransaction;

    private final UUID relatedOrderId;
    private final String relatedOrderNo;
    private final UUID relatedPayoutRequestId;
    private final UUID relatedItemWithdrawalRequestId;
    private final UUID relatedDegradedItemCaseId;

    private final BigDecimal commissionAmount;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class RelatedAccount {
        private final UUID accountId;
        private final String email;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ReferenceTransaction {
        private final UUID id;
        private final String transactionNo;
        private final WalletTransactionType type;
        private final BigDecimal amount;
    }
}
