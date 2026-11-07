package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// POST /tryon/live/sessions/{id}/video - URL công khai để app phát lại / tải về
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveTryOnSessionVideoResponse {
    private String videoUrl;
}
