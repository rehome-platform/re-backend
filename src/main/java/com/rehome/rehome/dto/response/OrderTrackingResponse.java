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
    private Instant deliveredAt;
    private List<OrderTrackingEventResponse> events;
}
