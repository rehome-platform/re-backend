package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDetailResponse {
    private String id;
    private String name;
    private BigDecimal currentPrice;
    private BigDecimal originalPrice;
    private ProductStatus status;
    private String brandName;
    private UUID categoryId;
    private String categoryName;
    private String size;
    private String condition;
    private String description;
    private Instant inspectionDate;
    private UUID warehouseId;
    private String warehouseName;
    private List<String> photos;
}
