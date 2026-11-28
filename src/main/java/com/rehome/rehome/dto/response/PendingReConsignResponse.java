package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ReConsignStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingReConsignResponse {
    private UUID decisionId;
    private String productId;
    private String productName;
    private BigDecimal currentPrice;
    private BigDecimal previousAgreedPrice;
    private Instant decidedAt;

    private ReConsignStatus reConsignStatus;
    private BigDecimal memberCounterPrice;
    private String memberCounterNote;
    private Instant counteredAt;
    private Instant moderatorResponseDeadline;
    private boolean overdue;
}
