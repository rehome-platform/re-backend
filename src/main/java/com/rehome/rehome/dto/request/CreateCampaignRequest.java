package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCampaignRequest {
    private String description;
    private Instant startTime;
    private Instant endTime;
    private String locationName;
    private BigDecimal latitude;
    private BigDecimal longitude;

    // Cột preferred_item_types của entity là 1 chuỗi -> service nối list lại bằng ", "
    private List<String> preferredItemTypes;
}
