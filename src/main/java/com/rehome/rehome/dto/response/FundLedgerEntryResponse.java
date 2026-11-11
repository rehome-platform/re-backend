package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * GET /api/admin/fund/ledger - một bút toán phía quỹ nền tảng (dòng đối ứng của một bút toán ví Member).
 * amount dương là tiền vào quỹ, âm là tiền ra khỏi quỹ.
 */
@Getter
@Builder
@AllArgsConstructor
public class FundLedgerEntryResponse {
    private final UUID id;
    private final String transactionNo;
    private final WalletTransactionType type;
    private final BigDecimal amount;
    // Số dư quỹ ngay sau bút toán này
    private final BigDecimal balanceAfter;
    private final TransactionStatus status;
    private final String note;
    private final Instant createdAt;

    // Tài khoản Member của dòng đối ứng; null nếu dòng quỹ không có đối ứng (dữ liệu cũ)
    private final RelatedAccount relatedAccount;
    // Bút toán ví Member mà dòng này đối ứng
    private final ReferenceTransaction referenceTransaction;

    // Chứng từ gốc sinh ra bút toán (chỉ có cái nào liên quan tới loại bút toán)
    private final UUID relatedOrderId;
    private final String relatedOrderNo;
    private final UUID relatedPayoutRequestId;
    private final UUID relatedItemWithdrawalRequestId;
    private final UUID relatedDegradedItemCaseId;

    // Chỉ có với ORDER_PAYOUT: phần hoa hồng nền tảng giữ lại của món được trả (giá chốt * tỷ lệ hoa hồng)
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
