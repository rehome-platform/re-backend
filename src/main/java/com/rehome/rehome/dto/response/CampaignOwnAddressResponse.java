package com.rehome.rehome.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignOwnAddressResponse {
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String receivingHours;
    private String contactPhone;
}
