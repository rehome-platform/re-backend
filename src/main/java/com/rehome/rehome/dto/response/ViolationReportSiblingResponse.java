package com.rehome.rehome.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một báo cáo khác cùng target, hiện trong GET /violation-reports/{id} ("otherReports"). */
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
