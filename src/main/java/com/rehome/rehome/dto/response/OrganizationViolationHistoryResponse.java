package com.rehome.rehome.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lịch sử vi phạm của 1 tổ chức, hiện trong GET /violation-reports/{id}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationViolationHistoryResponse {
    private String organizationId;
    // Số incident đã bị RESOLVED (1 target bị đóng 1 lần = 1 incident, không tính theo số báo cáo)
    private long totalViolationCount;
    private List<OrganizationViolationHistoryItem> actions;
}
