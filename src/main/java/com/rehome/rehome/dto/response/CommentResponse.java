package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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

    @JsonProperty("isMine")
    private boolean isMine;
}
