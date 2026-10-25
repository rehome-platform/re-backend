package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayOrdersResponse {
    private List<PaidOrderResponse> orders;

    // Tổng tiền đã trừ khỏi ví cho cả lô đơn
    private BigDecimal totalAmount;

    // Số dư ví sau khi trừ, để FE cập nhật ngay không cần gọi lại GET /wallet/balance
    private BigDecimal walletBalance;
}
