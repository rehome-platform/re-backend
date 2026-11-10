package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Chi tiết công khai 1 bài viết (F-086/OB-04). Guest xem được, không cần token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostDetailResponse {
    private String id;
    private String content;
    private List<String> photoUrls;
    private Instant createdAt;
    private String campaignId;
    private String campaignTitle;
    private PostOrganizationSummaryResponse organization;
    private long likeCount;
    private long commentCount;
    private boolean likedByMe;

    // Trạng thái bài viết: VISIBLE / REMOVED
    private String status;

    // Lý do Moderator gỡ bài (status = REMOVED); chỉ trả cho tổ chức sở hữu, null với mọi
    // viewer khác - Member/Guest nhận 404 trước khi tới được field này (CommunityPostServiceImpl)
    private String removalReason;
}
