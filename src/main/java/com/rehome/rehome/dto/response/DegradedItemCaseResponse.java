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
public class DegradedItemCaseResponse {
    private String id;
    private String productId;
    private String choice;
    private BigDecimal compensationAmount;
    private BigDecimal paidAmount;
    private Instant decidedAt;
}
