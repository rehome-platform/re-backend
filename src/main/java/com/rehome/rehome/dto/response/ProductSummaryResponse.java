package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.TryOnSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSummaryResponse {
    private String id;
    private String name;
    private String thumbnailUrl;
    private String brandName;
    private UUID categoryId;
    private String categoryName;
    private TryOnSlot tryonSlot;
    private String size;
    private String condition;
    private BigDecimal originalPrice;
    private BigDecimal currentPrice;
    private Boolean isMine;

    private boolean featured;
}
