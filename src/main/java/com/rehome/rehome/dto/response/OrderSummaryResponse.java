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
public class OrderSummaryResponse {
    private String id;
    private String orderNo;
    private String status;
    private BigDecimal totalAmount;
    private String warehouseName;
    private int itemCount;
    private String firstItemName;
    private String firstItemThumbnail;
    private Instant paymentDeadline;
    private Instant deliveredAt;
    private Instant autoCompleteDeadline;
    private Short serviceRating;
}
