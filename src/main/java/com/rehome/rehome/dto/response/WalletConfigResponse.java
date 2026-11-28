package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletConfigResponse {
    private BigDecimal minPayout;
    private Integer maxPayoutsPerDay;
    private Long usedToday;
}
