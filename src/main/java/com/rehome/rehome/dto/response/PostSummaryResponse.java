package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 1 dòng trong danh sách bài viết cộng đồng (F-086).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostSummaryResponse {
    private String id;
    private String organizationId;
    private String content;
    private String status;
    private List<String> photoUrls;
    private Instant createdAt;
    private String campaignId;
    private String campaignTitle;
    private PostOrganizationSummaryResponse organization;
    private long likeCount;
    private long commentCount;

    @JsonProperty("likedByMe")
    private boolean likedByMe;
}
