package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransactionResponse {
    private String id;
    private String transactionNo;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String relatedOrderId;
    private String relatedOrderNo;
    private String itemName;

    // Chỉ có với giao dịch WITHDRAWAL — dùng để mở GET /wallet/payout-requests/{id}
    private String payoutId;
    private String status;
    private String note;
    private Instant createdAt;
}
