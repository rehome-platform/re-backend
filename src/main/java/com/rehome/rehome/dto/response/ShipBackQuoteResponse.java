package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// GET /return-requests/{id}/ship-back-quote - báo phí gửi lại hàng trả không hợp lệ
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipBackQuoteResponse {
    private BigDecimal fee;
    private String addressId;
    private String recipientName;
    private String phone;
    private String addressDetail;
    private String wardName;
    private String provinceName;
    private String warehouseName;
    // Số phút người mua có để trả phí sau khi chọn SHIP_BACK
    private Integer feeDeadlineMinutes;
}
