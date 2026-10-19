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
public class OrderDetailItemResponse {
    private String productId;
    private String name;
    private String thumbnailUrl;
    private String size;
    private String brandName;
    private String condition;
    private BigDecimal priceAtOrder;
}
