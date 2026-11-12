package com.rehome.rehome.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chiến dịch đang nhận đồ tại 1 điểm ký gửi - GET /warehouses/{id}/campaigns.
 * Chỉ gồm các field màn quyên góp trực tiếp cần.
 */
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
