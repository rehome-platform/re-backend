package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tổ chức sở hữu chiến dịch, nhúng trong CampaignPublicResponse khi duyệt công khai
 * (GET /campaigns, GET /campaigns/{id}, GET /api/organizations/{id}/campaigns).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignOrganizationSummaryResponse {
    private String id;
    private String displayName;
    private String logoUrl;
}
