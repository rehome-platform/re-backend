package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class MyPostResponse {
    private String id;
    private String content;
    private List<String> photoUrls;
    private Instant createdAt;
    private String campaignId;
    private String campaignTitle;
    private long likeCount;
    private long commentCount;

    private long reportCount;

    @JsonProperty("likedByMe")
    private boolean likedByMe;

    private String status;

    private String removalReason;
}
