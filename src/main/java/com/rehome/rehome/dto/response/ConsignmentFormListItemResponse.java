package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class ConsignmentFormListItemResponse {
    private UUID id;
    private ConsignmentFormStatus status;
    private String productName;
    private BigDecimal desiredPrice;
    private String thumbnailUrl;
    private String warehouseName;
    private LocalDate bringInDeadline;
    private String rejectReason;
    private Instant createdAt;
}
