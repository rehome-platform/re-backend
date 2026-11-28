package com.rehome.rehome.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemWithdrawalDetailResponse {

    private String id;
    private String productId;
    private String productName;

    private String status;

    private String rejectReason;

    private BigDecimal feeAmount;

    private boolean feePaid;

    private String paymentMethod;

    private Instant pickupDeadline;

    private Instant feePaymentDeadline;

    private String warehouseName;

    private String warehouseAddress;
    private String warehouseOpeningHours;

    private String photoUrl;

    private Instant createdAt;

    private Instant completedAt;
}
