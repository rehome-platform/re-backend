package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1 dòng trong danh sách bài viết cộng đồng (F-086).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostSummaryResponse {
    private String id;
    private String organizationId;
    private String content;
    private String status;
}
