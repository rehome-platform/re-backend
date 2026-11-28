package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private String id;
    private String type;
    private String title;
    private String message;

    @JsonProperty("isRead")
    private boolean isRead;

    private String targetType;
    private String targetId;

    private String warehouseId;

    private Instant createdAt;
}
