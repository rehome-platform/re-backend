package com.rehome.rehome.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// GET /tryon/live/sessions - một video thử đồ real-time đã quay xong của chính Member
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveTryOnHistoryItemResponse {
    private UUID sessionId;
    private String productId;
    private String productName;
    // Ảnh đầu tiên của món, null nếu món không còn ảnh
    private String productThumbnailUrl;
    private String videoUrl;
    // Thời lượng thực tế mobile báo lại, có thể null
    private Integer actualDurationSeconds;
    private Instant createdAt;
    private Instant completedAt;
}
