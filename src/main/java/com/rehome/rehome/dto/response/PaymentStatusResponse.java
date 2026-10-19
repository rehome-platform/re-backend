package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentStatusResponse {
    private UUID orderId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String momoTransactionId;
    private Instant paidAt;
}
