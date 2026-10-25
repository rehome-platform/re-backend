package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentStatusResponse {
    // Mọi đơn thuộc cùng một giao dịch cổng thanh toán; thường chỉ có một phần tử
    private List<UUID> orderIds;

    private PaymentStatus status;

    // Tổng tiền của cả giao dịch, cộng hết các đơn trong orderIds
    private BigDecimal amount;
    private String momoTransactionId;
    private String vnpTransactionNo;
    private Instant paidAt;
}
