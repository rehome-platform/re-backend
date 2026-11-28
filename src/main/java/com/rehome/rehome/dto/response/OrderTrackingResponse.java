package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTrackingResponse {
    private String id;
    private String orderNo;
    private String status;
    private String ghnTrackingCode;

    private Instant expectedDeliveryDate;

    private Instant deliveredAt;

    private Boolean hasIssue;

    private String issueNote;

    private List<OrderTrackingEventResponse> events;
}
