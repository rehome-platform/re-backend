package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1 dòng trong danh sách chiến dịch của tổ chức (F-103).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignSummaryResponse {
    private String id;
    private String description;
    private String status;
}
