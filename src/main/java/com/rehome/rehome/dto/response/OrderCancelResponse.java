package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCancelResponse {
    private String id;
    private String status;

    // null khi hủy đơn chưa thanh toán - không có tiền nào để hoàn
    private BigDecimal refundAmount;
}
