package com.rehome.rehome.dto.response;

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
public class ViolationReportDetailResponse {
    private String id;
    private String targetType;
    private String targetId;
    private String status;
    private String reason;
    private Instant createdAt;

    private String resolutionAction;
    private String resolutionReason;
    private Instant resolvedAt;

    private ViolationTargetSnapshotResponse targetSnapshot;
    private List<ViolationReportSiblingResponse> otherReports;
    private OrganizationViolationHistoryResponse organizationViolationHistory;
}
