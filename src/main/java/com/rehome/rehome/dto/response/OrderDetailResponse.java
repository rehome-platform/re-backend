package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailResponse {
    private String id;
    private String orderNo;
    private String status;

    private Instant createdAt;
    private String paymentMethod;
    private Instant paidAt;

    // Hạn thanh toán, để màn chờ thanh toán đếm ngược; chỉ còn ý nghĩa khi status = PENDING_PAYMENT
    private Instant paymentDeadline;

    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;

    // Kho xử lý đơn — dùng để mở hội thoại "Nhắn cho kho"
    private String warehouseId;
    private String warehouseName;

    private String shippingRecipientName;
    private String shippingPhone;
    private String shippingAddressDetail;
    private String shippingWardName;
    private String shippingProvinceName;

    private List<OrderDetailItemResponse> items;

    private String ghnTrackingCode;
    private Instant deliveredAt;
    private Instant autoCompleteDeadline;
    private Instant completedAt;
    private Short serviceRating;
    private Instant ratedAt;
    private String ratingComment;

    private String cancelReason;
    private Instant cancelledAt;
    private BigDecimal refundAmount;

    // Yêu cầu trả hàng gần nhất của đơn, null nếu chưa từng tạo — có giá trị thì nút trả hàng
    // chuyển thành "Xem yêu cầu trả hàng"
    private String returnRequestId;

    // Hạn cuối còn được gửi yêu cầu trả hàng (giao hàng + số ngày cho phép).
    // null khi đơn chưa giao, vì lúc đó chưa tính được mốc.
    private Instant returnDeadline;
}
