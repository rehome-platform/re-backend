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
public class ViolationReportGroupResponse {
    private String id;
    private String targetType;
    private String targetId;
    private long reportCount;
    private String sampleReason;
    private Instant oldestReportedAt;
    private Instant latestReportedAt;
}
