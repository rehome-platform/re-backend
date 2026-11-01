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

// Dùng chung cho GET /moderator/consignment-forms (danh sách) và GET /moderator/consignment-forms/{id} (chi tiết).
// Các field chỉ có ở chi tiết được bỏ khỏi JSON của danh sách khi null.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeratorConsignmentFormResponse {

    // ---- Thông tin form (có ở cả danh sách và chi tiết)
    private UUID id;
    private String formNo;
    private ConsignmentFormStatus status;
    private String productName;
    private BigDecimal desiredPrice;
    // Ảnh đại diện (ảnh đầu tiên)
    private String thumbnailUrl;
    private String rejectReason;
    private LocalDate bringInDeadline;
    private Instant createdAt;
    private String warehouseName;

    private MemberInfo member;
    // Moderator phụ trách theo giai đoạn: claim hàng đợi trước khi có biên nhận, moderator trên biên nhận sau đó
    private ModeratorInfo moderator;

    // Biên nhận mới nhất của form
    private UUID receiptId;
    // Lúc Member xác nhận biên nhận mới nhất; null nếu chưa xác nhận hoặc chưa có biên nhận
    private Instant confirmedAt;
    // SKU sản phẩm sau khi nhập kho; null nếu chưa nhập kho
    private String productSku;
    // Bước nghiệp vụ tiếp theo, Frontend dùng để điều hướng
    private ModeratorResumeStep resumeStep;

    // ---- Chỉ có ở chi tiết
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
    public static class ProductInfo {
        private String sku;
        private String name;
        private ProductStatus status;
        private String shelfLocation;
        private Instant createdAt;
    }
}