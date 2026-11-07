package com.rehome.rehome.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// POST /tryon/live/sessions - tất cả thông tin WebView cần để tự kết nối Decart, không lộ
// permanent API key: apiKey ở đây là client token ngắn hạn do backend mint hộ.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveTryOnSessionResponse {
    private UUID sessionId;
    private String apiKey;
    private Instant expiresAt;
    private String modelId;
    private String garmentImageUrl;
    private String prompt;
    private int maxDurationSeconds;
    private int remainingToday;
}
