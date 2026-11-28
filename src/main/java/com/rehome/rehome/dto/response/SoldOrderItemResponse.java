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
public class SoldOrderItemResponse {
    private String productId;
    private String productName;
    private String thumbnailUrl;

    private BigDecimal salePrice;

    private Short termApplied;
    private BigDecimal commissionRate;
    private BigDecimal commission;
    private BigDecimal tax;

    private BigDecimal netAmount;
}
