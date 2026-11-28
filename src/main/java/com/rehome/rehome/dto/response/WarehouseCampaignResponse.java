package com.rehome.rehome.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseCampaignResponse {
    private String id;
    private String title;
    private String coverUrl;
    private Instant endTime;
    private List<String> preferredItemTypes;
    private String organizationId;
    private String organizationName;
    private String organizationLogoUrl;
}
