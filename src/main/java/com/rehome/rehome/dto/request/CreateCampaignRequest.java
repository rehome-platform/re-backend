package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCampaignRequest {
    private String title;
    private String description;
    private Instant startTime;
    private Instant endTime;

    private String coverUrl;

    private Boolean receiveAtOwnAddress;

    private List<String> warehouseIds;

    private List<String> preferredItemTypes;
}
