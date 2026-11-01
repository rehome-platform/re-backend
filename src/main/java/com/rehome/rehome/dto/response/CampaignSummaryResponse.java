package com.rehome.rehome.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1 dòng trong danh sách chiến dịch của tổ chức (F-103, OC-03).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignSummaryResponse {
    private String id;
    private String title;
    private String description;
    private String coverUrl;
    private Instant startTime;
    private Instant endTime;
    private String status;

    private boolean receiveAtOwnAddress;
    private CampaignOwnAddressResponse ownAddress;
    private List<CampaignWarehouseResponse> warehouses;
}
