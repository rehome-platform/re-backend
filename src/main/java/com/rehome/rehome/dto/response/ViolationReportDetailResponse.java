package com.rehome.rehome.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** GET /violation-reports/{id} - Moderator xem đủ thông tin để ra quyết định trên 1 báo cáo. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationReportDetailResponse {
    private String id;
    private String targetType;
    private String targetId;
    // PENDING / RESOLVED / DISMISSED
    private String status;
    // Lý do của chính báo cáo này (người báo cáo nhập)
    private String reason;
    private Instant createdAt;

    // Chỉ có khi đã resolve: hành động đã làm, lý do của Moderator, thời điểm xử lý
    private String resolutionAction;
    private String resolutionReason;
    private Instant resolvedAt;

    private ViolationTargetSnapshotResponse targetSnapshot;
    // Các báo cáo khác cùng target (không gồm báo cáo này)
    private List<ViolationReportSiblingResponse> otherReports;
    // null khi không xác định được tổ chức sở hữu target (dữ liệu bị xoá)
    private OrganizationViolationHistoryResponse organizationViolationHistory;
}
