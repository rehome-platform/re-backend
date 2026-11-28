package com.rehome.rehome.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DegradedItemCaseDetailResponse {

    private String id;
    private String productId;
    private String productName;

    private List<String> photos;

    private String reason;

    private String moderatorNote;

    private BigDecimal compensationAmount;

    private BigDecimal degradedCompensationRate;

    private BigDecimal paidAmount;

    private Instant receivedAt;

    private Instant decidedAt;

    private Instant decisionDeadline;

    private Instant pickupDeadline;

    private boolean autoDecided;

    private Instant pickedUpAt;

    private String status;

    private String decision;
}
