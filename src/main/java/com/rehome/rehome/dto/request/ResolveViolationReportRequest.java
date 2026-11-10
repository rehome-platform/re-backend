package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolveViolationReportRequest {
    // REMOVE_POST / HIDE_CAMPAIGN / WARN_ORG / PROPOSE_BAN
    private String action;

    // Lý do Moderator xử lý - khác reason của người báo cáo, trả cho tổ chức biết vì sao bị xử lý
    private String reason;
}
