package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Một đơn có chứa hàng của người bán đang đăng nhập.
 *
 * Đơn được tách theo KHO chứ không theo người ký gửi, nên một đơn có thể gồm hàng của
 * nhiều người bán khác nhau. Vì vậy {@code items} chỉ chứa các món CỦA NGƯỜI ĐANG XEM,
 * và {@code itemCount}/{@code subtotal} cũng chỉ tính trên các món đó - không phải tổng
 * của đơn. Response cố tình không có tên/điện thoại/địa chỉ người mua: người bán không
 * cần biết, kho mới là bên đóng gói và giao.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoldOrderResponse {
    private String orderId;
    private String orderNo;
    private String status;
    private String warehouseName;

    // Chỉ đếm/cộng các món của người đang xem
    private int itemCount;
    private BigDecimal subtotal;
    private BigDecimal netAmount;

    // true = đơn đã COMPLETED, tiền đã vào ví; false = netAmount chỉ là dự kiến
    private Boolean paidOut;

    private Instant createdAt;
    private Instant deliveredAt;
    private Instant completedAt;
    private Instant cancelledAt;

    private List<SoldOrderItemResponse> items;
}
