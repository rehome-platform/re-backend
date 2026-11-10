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
public class ItemWithdrawalResponse {
    private String id;
    private String productId;
    private BigDecimal feeCharged;
    private String status;
    private Instant pickupDeadline;

    // Hạn trả phí (feeCharged > 0); null khi miễn phí - không có gì phải trả
    private Instant feePaymentDeadline;

    // Lý do Moderator từ chối; null trừ khi status = REJECTED
    private String rejectReason;
}
