package com.rehome.rehome.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Kết quả đồng bộ trạng thái thanh toán phí gửi lại hàng trả không hợp lệ (app hỏi lại khi quay về từ VNPay)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipBackPaymentStatusResponse {
    private UUID returnRequestId;
    private boolean paid;
    private String shipBackStatus;
    private BigDecimal amount;
    private Instant paidAt;
}
