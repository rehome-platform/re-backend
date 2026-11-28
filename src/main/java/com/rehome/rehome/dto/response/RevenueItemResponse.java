package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class RevenueItemResponse {
    private final UUID orderItemId;
    private final String productId;
    private final String productName;
    private final UUID orderId;
    private final String orderNo;
    private final long price;
    private final Short termApplied;
    private final BigDecimal commissionRate;
    private final long commissionAmount;
    private final Instant completedAt;
}
