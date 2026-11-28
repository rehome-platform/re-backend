package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {
    private String id;
    private String senderId;
    private String content;
    private String originalContent;
    private List<String> imageUrls;
    private Instant createdAt;

    @JsonProperty("isMine")
    private boolean isMine;
}
