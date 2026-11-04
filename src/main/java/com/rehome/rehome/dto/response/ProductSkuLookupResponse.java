package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSkuLookupResponse {
    private String id;
    private String name;
    private ProductStatus status;

    private String brandName;
    private String categoryName;
    private String size;
    private String conditionNote;

    private BigDecimal currentPrice;
    private BigDecimal originalPrice;
    private BigDecimal floorPrice;

    private Short currentTerm;
    private Integer daysIntoTerm;

    private String memberCode;
    private String memberFullName;

    private Instant stockInDate;
    private List<String> photos;
}