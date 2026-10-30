package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.ProductCondition;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConsignmentFormRequest {
    private UUID categoryId;
    private UUID brandId;
    private String productName;
    private String description;
    private BigDecimal desiredPrice;
    private UUID warehouseId;
    private List<String> photoUrls;
    @Size(max = 50)
    private String size;
    private ProductCondition condition;
}
