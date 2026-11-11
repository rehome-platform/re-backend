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
import java.util.UUID;

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

    // Snapshot điều khoản ký gửi lấy từ ConsignmentReceipt của sản phẩm
    private Integer termLengthDays;
    private BigDecimal commissionRateTerm1;
    private BigDecimal commissionRateTerm2;
    private BigDecimal commissionRateTerm3;
    private BigDecimal degradedCompensationRate;

    private String memberCode;
    private String memberFullName;

    private Instant stockInDate;
    private List<String> photos;

    // Quyết định hết hạn 3 kỳ đang chờ Member chọn (F-029); null = chưa tới cuối kỳ hoặc đã quyết định xong
    private UUID decisionId;
    // Ca xử lý hàng xuống cấp đang chờ Member quyết định (F-036); null = không có ca nào đang mở
    private UUID degradedCaseId;
}