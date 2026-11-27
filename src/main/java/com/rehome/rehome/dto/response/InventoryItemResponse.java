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
    // Giá hai bên chốt lúc ký gửi (ConsignmentReceipt.agreedPrice)
    private BigDecimal agreedPrice;
    // Độ dài một kỳ ký gửi (snapshot trên biên nhận)
    private Integer termLengthDays;

    // Kỳ ký gửi hiện tại của sản phẩm (1/2/3)
    private Short currentTerm;
    // Số ngày đã trôi qua trong kỳ hiện tại, tối thiểu 1; null nếu sản phẩm chưa có mốc bắt đầu kỳ
    private Integer daysIntoTerm;
    // Giá đang bán hiện tại (sau các lần hạ giá của Member)
    private BigDecimal currentPrice;
}
