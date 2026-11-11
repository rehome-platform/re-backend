package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** GET /api/admin/revenue/items - một món đã bán (thuộc đơn COMPLETED). Tiền tính bằng đồng (VND), làm tròn số nguyên. */
@Getter
@Builder
@AllArgsConstructor
public class RevenueItemResponse {
    private final UUID orderItemId;
    // Sản phẩm
    private final String productId;
    private final String productName;
    // Đơn hàng
    private final UUID orderId;
    private final String orderNo;
    // Giá bán chốt lúc đặt đơn
    private final long price;
    // Kỳ ký gửi (1/2/3) áp dụng lúc đặt đơn; null nếu món cũ không lưu kỳ
    private final Short termApplied;
    // Tỷ lệ hoa hồng đã áp dụng (0.10 = 10%) và số hoa hồng thu được = price * tỷ lệ
    private final BigDecimal commissionRate;
    private final long commissionAmount;
    // Thời điểm đơn hoàn thành
    private final Instant completedAt;
}
