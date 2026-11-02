package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Người bình luận, nhúng trong CommentResponse (F-087). type: MEMBER hoặc ORGANIZATION
 * (Account.role) — tổ chức chỉ bình luận/trả lời được trên bài của chính mình.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentAuthorResponse {
    private String id;
    private String type;
    private String name;
    private String avatarUrl;
}
