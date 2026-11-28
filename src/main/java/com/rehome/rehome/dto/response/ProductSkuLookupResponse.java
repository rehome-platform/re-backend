package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.TryOnSlot;
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
    private UUID pendingReturnRequestId;
    private UUID activeWithdrawalId;
    private ItemWithdrawalStatus activeWithdrawalStatus;
    private String id;
    private String name;
    private ProductStatus status;

    private String brandName;
    private UUID categoryId;
    private String categoryName;
    private TryOnSlot tryonSlot;
    private String size;
    private String conditionNote;

    private BigDecimal currentPrice;
    private BigDecimal originalPrice;
    private BigDecimal floorPrice;

    private Short currentTerm;
    private Integer daysIntoTerm;

    private Integer termLengthDays;
    private BigDecimal commissionRateTerm1;
    private BigDecimal commissionRateTerm2;
    private BigDecimal commissionRateTerm3;
    private BigDecimal degradedCompensationRate;

    private String memberCode;
    private String memberFullName;

    private Instant stockInDate;
    private List<String> photos;

    private UUID decisionId;
    private UUID degradedCaseId;
}
