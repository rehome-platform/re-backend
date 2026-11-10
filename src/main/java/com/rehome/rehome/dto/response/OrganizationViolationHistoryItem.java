package com.rehome.rehome.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 1 lần tổ chức bị xử lý vi phạm trong quá khứ (1 incident = 1 target được resolve 1 lần). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationViolationHistoryItem {
    private String targetType;
    private String targetId;
    // Tên action lúc resolve (REMOVE_POST/HIDE_CAMPAIGN/WARN_ORG/PROPOSE_BAN)
    private String action;
    private String reason;
    private Instant date;
}
