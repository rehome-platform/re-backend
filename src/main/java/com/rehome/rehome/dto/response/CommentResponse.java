package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private com.rehome.rehome.entity.enums.ContentReviewStatus reviewStatus;
    private CommentAuthorResponse author;
    private String reviewReason;
    private Instant createdAt;

    // Bình luận này có phải của chính người đang xem không; false khi Guest (không có viewerId)
    @JsonProperty("isMine")
    private boolean isMine;
}
