package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalChangeRequestDetailResponse {
    private String id;

    private String status;

    private JsonNode proposedChanges;
    private String rejectReason;
    private Instant createdAt;
    private Instant decidedAt;
}
