package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConsignmentFormListItemResponse {
    private UUID id;
    private ConsignmentFormStatus status;
    private String productName;
    private BigDecimal desiredPrice;
}
