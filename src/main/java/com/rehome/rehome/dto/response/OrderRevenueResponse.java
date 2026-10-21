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
public class OrderRevenueResponse {
    private String orderId;
    private String orderNo;
    private BigDecimal salePrice;
    private BigDecimal commission;
    private BigDecimal tax;
    private BigDecimal netAmount;
    private Instant completedAt;
}
