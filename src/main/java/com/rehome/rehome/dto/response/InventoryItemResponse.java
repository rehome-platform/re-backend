package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ProductStatus;
import jakarta.persistence.Column;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemResponse {
    private String id;
    private String name;
    private ProductStatus status;
    private BigDecimal agreedPrice;
    private Integer termLengthDays;

}