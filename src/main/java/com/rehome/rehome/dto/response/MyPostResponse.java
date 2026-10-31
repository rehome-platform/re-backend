package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1 dòng trong danh sách bài viết của tổ chức, kèm số tương tác (F-107).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyPostResponse {
    private String id;
    private String content;
    private long likeCount;
    private long commentCount;
}
