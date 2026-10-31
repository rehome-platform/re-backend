package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chi tiết công khai 1 bài viết (F-086). Guest xem được, không cần token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostDetailResponse {
    private String id;
    private String content;
    private long likeCount;
    private long commentCount;
    private boolean likedByMe;
}
