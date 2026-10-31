package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bài viết vừa đăng (F-104).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostCreateResponse {
    private String id;
    private String status;
}
