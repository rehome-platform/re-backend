package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItemResponse {
    private String id;
    private String name;
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