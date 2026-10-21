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
public class PayoutRequestResponse {
    private String id;
    private String payoutNo;
    private BigDecimal amount;
    private String status;
    private String failureReason;
    private Instant createdAt;
    private Instant processedAt;
}
