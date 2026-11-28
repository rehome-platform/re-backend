package com.rehome.rehome.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipBackPaymentStatusResponse {
    private UUID returnRequestId;
    private boolean paid;
    private String shipBackStatus;
    private BigDecimal amount;
    private Instant paidAt;
}
