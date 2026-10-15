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
public class ProductSummaryResponse {
    private String id;
    private String name;
    private String thumbnailUrl;
    private String brandName;
    private String categoryName;
    private String size;
    private String condition;
    private BigDecimal originalPrice;
    private BigDecimal currentPrice;
}
