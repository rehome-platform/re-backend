package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 1 dòng trong danh sách bài viết của tổ chức, kèm số tương tác (F-107/OB-01).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyPostResponse {
    private String id;
    private String content;
    private List<String> photoUrls;
    private Instant createdAt;
    private String campaignId;
    private String campaignTitle;
    private long likeCount;
    private long commentCount;

    // Số báo cáo vi phạm đang chờ xử lý (PENDING) nhắm vào bài này
    private long reportCount;

    // Chính tổ chức đang gọi /me/posts đã thích bài này chưa
    @JsonProperty("likedByMe")
    private boolean likedByMe;

    // Trạng thái bài viết: VISIBLE / REMOVED - để FE hiển thị badge gỡ bài ngay trên list (F-107)
    private String status;

    // Lý do Moderator gỡ bài (status = REMOVED); luôn là tổ chức sở hữu nên không cần che
    // như PostDetailResponse (CommunityPostServiceImpl.getDetail)
    private String removalReason;
}
