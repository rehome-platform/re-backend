package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// GET /tryon/live/quota - mobile gọi trước khi mở camera để hiện/ẩn nút, báo "hết lượt hôm nay"
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveTryOnQuotaResponse {
    private int dailyLimit;
    private int usedToday;
    private int remaining;
    private int maxDurationSeconds;
}
