package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Một món hàng của người bán trong một đơn. Chỉ chứa dữ liệu về món và tiền của chính
 * người bán - không có gì về người mua.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoldOrderItemResponse {
    private String productId;
    private String productName;
    private String thumbnailUrl;

    // Giá bán chốt lúc đặt đơn (OrderItem.priceAtOrder), không đổi theo giá hiện tại
    private BigDecimal salePrice;

    private Short termApplied;
    private BigDecimal commissionRate;
    private BigDecimal commission;
    private BigDecimal tax;

    // Tiền người bán nhận = salePrice - commission. Cùng công thức với lúc payout thật
    // trong confirmReceipt, nên đơn đã COMPLETED sẽ khớp số với GET /wallet/revenue.
    // Đơn chưa COMPLETED thì đây là số DỰ KIẾN, xem cờ paidOut.
    private BigDecimal netAmount;
}
