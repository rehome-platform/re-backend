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
public class ShippingFeePreviewResponse {
    private String productId;
    private String warehouseId;
    private String warehouseName;
    private String wardName;
    private String provinceName;
    private BigDecimal shippingFee;
}
