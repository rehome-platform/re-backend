package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// API 96 - GET /tryon/suggestions: món gợi ý để ghép đủ outfit
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TryOnSuggestionResponse {
    private String id;
    private String name;
    private BigDecimal currentPrice;
}
