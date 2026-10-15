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
public class CartWarehouseGroupResponse {
    private String warehouseId;
    private String warehouseName;
    private List<CartItemDetailResponse> items;
    private BigDecimal subtotal;
}
