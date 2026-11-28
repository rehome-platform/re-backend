package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignWarehouseResponse {
    private String id;
    private String name;
    private String address;
    private String openingHours;
    private long pendingCount;
}
