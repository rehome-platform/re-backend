package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bài viết sau khi sửa nội dung (F-105).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostUpdateResponse {
    private String id;
    private String content;
}
