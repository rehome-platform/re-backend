package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoldOrderResponse {
    private String orderId;
    private String orderNo;
    private String status;
    private String warehouseName;

    private int itemCount;
    private BigDecimal subtotal;
    private BigDecimal netAmount;

    private Boolean paidOut;

    private Instant createdAt;
    private Instant deliveredAt;
    private Instant completedAt;
    private Instant cancelledAt;

    private List<SoldOrderItemResponse> items;
}
