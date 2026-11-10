package com.rehome.rehome.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /violation-reports: 1 dòng = 1 target đang có báo cáo khớp bộ lọc, gộp từ nhiều báo cáo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationReportGroupResponse {
    // Id của báo cáo cũ nhất trong nhóm - dùng để gọi GET /violation-reports/{id}
    private String id;
    private String targetType;
    private String targetId;
    private long reportCount;
    // Lý do của báo cáo cũ nhất, xem trước nhanh trong danh sách
    private String sampleReason;
    private Instant oldestReportedAt;
    private Instant latestReportedAt;
}
