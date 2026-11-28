package com.rehome.rehome.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationReportSiblingResponse {
    private String id;
    private String reporterId;
    private String reason;
    private String status;
    private Instant createdAt;
}
