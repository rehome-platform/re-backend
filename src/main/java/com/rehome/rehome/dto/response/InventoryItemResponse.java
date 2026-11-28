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
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItemResponse {
    private java.util.UUID pendingReturnRequestId;
    private UUID activeWithdrawalId;
    private ItemWithdrawalStatus activeWithdrawalStatus;
    private String id;
    private String name;
    private String thumbnailUrl;
    private List<String> photos;
    private UUID categoryId;
    private String categoryName;
    private TryOnSlot tryonSlot;
    private ProductStatus status;
    private BigDecimal agreedPrice;
    private Integer termLengthDays;

    private Short currentTerm;
    private Integer daysIntoTerm;
    private BigDecimal currentPrice;
}
