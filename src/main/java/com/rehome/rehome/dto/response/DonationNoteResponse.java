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
public class DonationNoteResponse {
    private String id;
    private String campaignId;
    private String donorName;

    @JsonProperty("isAnonymous")
    private boolean isAnonymous;

    private String note;
    private Instant createdAt;
}
