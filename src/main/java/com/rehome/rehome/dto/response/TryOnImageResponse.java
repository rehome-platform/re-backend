package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.TryOnModelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TryOnImageResponse {
    private UUID id;
    private TryOnModelType modelType;
    private String imageUrl;
    private boolean reused;
    private Instant createdAt;

    private ProductInfo top;
    private ProductInfo bottom;
    private ProductInfo dress;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductInfo {
        private String id;
        private String name;
        private String thumbnailUrl;
        private BigDecimal currentPrice;
        private ProductStatus status;
        private UUID warehouseId;
    }
}
