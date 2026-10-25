package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutPreviewOrderResponse {
    private String warehouseId;
    private List<String> items;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal total;
}
