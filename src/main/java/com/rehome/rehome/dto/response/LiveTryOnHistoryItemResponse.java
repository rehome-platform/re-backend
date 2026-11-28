package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ProductStatus;
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
public class LiveTryOnHistoryItemResponse {
    private UUID sessionId;
    private String productId;
    private String productName;
    private String productThumbnailUrl;
    private ProductStatus productStatus;
    private Boolean isMine;
    private String videoUrl;
    private Integer actualDurationSeconds;
    private Instant createdAt;
    private Instant completedAt;
}
