package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Báo cáo vi phạm vừa gửi (F-088).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationReportResponse {
    private String id;
    private String status;
}
