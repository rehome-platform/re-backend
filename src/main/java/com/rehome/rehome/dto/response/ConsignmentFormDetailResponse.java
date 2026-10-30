package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.ProductCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class ConsignmentFormDetailResponse {
    private UUID id;
    private String formNo;
    private ConsignmentFormStatus status;
    private String productName;
    private String description;
    private BigDecimal desiredPrice;
    private String size;
    private ProductCondition condition;
    private List<String> photoUrls;
    private UUID categoryId;
    private String categoryName;
    private UUID brandId;
    private String brandName;
    private WarehouseInfo warehouse;
    private String rejectReason;
    private LocalDate bringInDeadline;
    private Instant createdAt;
    private UUID receiptId;
    private boolean receiptConfirmed;

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class WarehouseInfo {
        private UUID id;
        private String name;
        private String address;
        private String openingHours;
    }
}
