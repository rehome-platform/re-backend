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
public class WarehouseOrderResponse {
    private String id;
    private String orderNo;
    private String status;
    private Instant createdAt;
    private BigDecimal totalAmount;

    private int itemCount;
    private String firstItemName;
    private String firstItemThumbnail;

    private String buyerName;
    private String buyerPhone;

    private int scannedCount;
    private int totalCount;
    private String ghnTrackingCode;

    private Instant deliveredAt;
    private Instant completedAt;
    private Instant cancelledAt;
}
