package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.ModeratorResumeStep;
import com.rehome.rehome.entity.enums.ProductCondition;
import com.rehome.rehome.entity.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeratorConsignmentFormResponse {

    private UUID id;
    private String formNo;
    private ConsignmentFormStatus status;
    private String productName;
    private BigDecimal desiredPrice;
    private String thumbnailUrl;
    private String rejectReason;
    private LocalDate bringInDeadline;
    private Instant createdAt;
    private String warehouseName;

    private MemberInfo member;
    private ModeratorInfo moderator;

    private UUID receiptId;
    private Instant confirmedAt;
    private String productSku;
    private ModeratorResumeStep resumeStep;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String description;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String size;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ProductCondition condition;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> photoUrls;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UUID categoryId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String categoryName;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UUID brandId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String brandName;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private WarehouseInfo warehouse;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ReceiptInfo receipt;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ProductInfo product;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ConsignmentConfigInfo consignmentConfig;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MemberInfo {
        private UUID id;
        private String fullName;
        private String memberCode;
        private String phone;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ModeratorInfo {
        private UUID id;
        private String fullName;
        private String moderatorCode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WarehouseInfo {
        private UUID id;
        private String name;
        private String address;
        private String openingHours;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReceiptInfo {
        private UUID id;
        private String receiptNo;
        private BigDecimal agreedPrice;
        private BigDecimal floorPrice;
        private Integer termLengthDays;
        private String conditionNote;
        private String moderatorNote;
        private Instant confirmedAt;
        private Instant createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ConsignmentConfigInfo {
        private Integer termLengthDays;
        private Integer maxRenewals;
        private Integer freeWithdrawalWindowDays;
        private BigDecimal floorPriceRatio;
        private BigDecimal commissionRateTerm1;
        private BigDecimal commissionRateTerm2;
        private BigDecimal commissionRateTerm3;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProductInfo {
        private String sku;
        private String name;
        private ProductStatus status;
        private Instant createdAt;
    }
}