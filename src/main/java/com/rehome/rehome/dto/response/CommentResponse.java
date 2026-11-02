package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 1 bình luận của bài viết cộng đồng (F-087): dùng chung cho danh sách và cho response tạo mới.
 * parentId null = bình luận gốc; mobile tự xếp nhóm theo parentId (chỉ 1 cấp).
 * status == REMOVED: Moderator đã ẩn, content trả về null nhưng dòng vẫn được trả (OB-05).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentResponse {
    private String id;
    private String parentId;
    private String content;
    private String status;
    private CommentAuthorResponse author;
    private Instant createdAt;
}
