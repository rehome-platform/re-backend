package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

// API 96 - GET /tryon/suggestions: món gợi ý để ghép đủ outfit
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TryOnSuggestionResponse {
    private String id;
    private String name;
    private String thumbnailUrl;
    private String brandName;
    private String categoryName;
    private String size;
    private String condition;
    private BigDecimal originalPrice;
    private BigDecimal currentPrice;
    private UUID warehouseId;
}
