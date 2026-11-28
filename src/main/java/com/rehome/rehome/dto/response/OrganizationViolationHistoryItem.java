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
public class OrganizationViolationHistoryItem {
    private String targetType;
    private String targetId;
    private String action;
    private String reason;
    private Instant date;
}
