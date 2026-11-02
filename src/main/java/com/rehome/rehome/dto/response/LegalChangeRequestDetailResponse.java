package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Yêu cầu đổi thông tin pháp lý gần nhất của chính tổ chức (OP-05 "Trạng thái yêu cầu").
 * decidedAt null khi còn PENDING. rejectReason null trừ khi status = REJECTED.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalChangeRequestDetailResponse {
    private String id;

    /** PENDING / APPROVED / REJECTED */
    private String status;

    private JsonNode proposedChanges;
    private String rejectReason;
    private Instant createdAt;
    private Instant decidedAt;
}
