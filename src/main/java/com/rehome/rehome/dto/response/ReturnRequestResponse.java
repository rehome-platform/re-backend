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
public class ReturnRequestResponse {
    private String id;
    private String orderId;
    private String orderNo;
    private String status;
    private String reason;
    private List<String> photoUrls;

    private String ghnReturnTrackingCode;
    private Instant handoverDeadline;

    private String invalidReturnChoice;
    private Instant pickupDeadline;

    private String resolutionNote;
    private BigDecimal refundAmount;

    private Instant createdAt;
    private Instant resolvedAt;
}
