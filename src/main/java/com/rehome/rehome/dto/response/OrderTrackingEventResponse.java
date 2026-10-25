package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTrackingEventResponse {
    private String status;
    private String description;

    // Kho GHN đang giữ hàng lúc xảy ra sự kiện; GHN không phải lúc nào cũng gửi nên có thể null
    private String location;

    private Instant timestamp;
}
