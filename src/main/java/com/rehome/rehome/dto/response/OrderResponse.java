package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private String id;
    private String orderNo;
    private String warehouseId;
    private String shippingRecipientName;
    private String shippingPhone;
    private String shippingAddressDetail;
    private String shippingWardName;
    private String shippingProvinceName;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private String status;
    // Phần giảm giá của RIÊNG đơn này. Giỏ nhiều món -> nhiều đơn, mỗi đơn mang phần của mình,
    // màn "Đặt hàng thành công" phải cộng totalAmount các đơn chứ không tính ngược từ giá gốc.
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    // null khi đơn không dùng mã
    private String voucherCode;
    private String voucherName;

    private Instant paymentDeadline;
    private Instant addressChangedAt;

    // Số tiền hoàn lại vào ví khi đổi địa chỉ giao hàng làm giảm phí ship - 0 nếu không có hoàn tiền
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;
}
