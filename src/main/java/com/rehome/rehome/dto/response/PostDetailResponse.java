package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

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

    private String status;

    private String removalReason;
}
