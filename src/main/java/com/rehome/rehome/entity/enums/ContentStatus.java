package com.rehome.rehome.entity.enums;

public enum ContentStatus {
    // CommunityPost: REMOVED = gỡ hẳn khỏi sàn, bị loại khỏi mọi listing.
    // PostComment: REMOVED = Moderator ẩn, dòng vẫn được trả về (GET /posts/{id}/comments)
    // nhưng content = null ở tầng response (CommunityPostServiceImpl.toCommentResponse).
    VISIBLE,
    PENDING_REVIEW,
    REMOVED
}
