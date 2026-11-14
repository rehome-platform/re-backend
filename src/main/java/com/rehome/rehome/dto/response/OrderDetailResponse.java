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
    // M-08 hiện dòng "Giảm giá (SHIP-7K2M9A)  − 15.000đ" khi discountAmount > 0
    private BigDecimal discountAmount;
    private String voucherCode;
    private String voucherName;
    // = subtotal + shippingFee − discountAmount. Màn hủy đơn phải hiện ĐÚNG số này là
    // "số tiền hoàn", không phải giá gốc.
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
    // Ngày giao dự kiến do GHN trả về lúc tạo vận đơn, null nếu chưa có vận đơn
    private Instant expectedDeliveryDate;
    // Thời điểm kho tạo vận đơn; null nếu chưa có vận đơn hoặc đơn cũ tạo trước khi có cột này
    private Instant shipmentCreatedAt;
    private Instant deliveredAt;
    private Instant autoCompleteDeadline;
    private Instant completedAt;
    private Short serviceRating;
    private Instant ratedAt;
    private String ratingComment;
    private List<String> ratingImageUrls;

    private String cancelReason;
    private Instant cancelledAt;
    private BigDecimal refundAmount;
    private Instant addressChangedAt;

    // Yêu cầu trả hàng gần nhất của đơn, null nếu chưa từng tạo — có giá trị thì nút trả hàng
    // chuyển thành "Xem yêu cầu trả hàng"
    private String returnRequestId;

    // Hạn cuối còn được gửi yêu cầu trả hàng (giao hàng + số ngày cho phép).
    // null khi đơn chưa giao, vì lúc đó chưa tính được mốc.
    private Instant returnDeadline;

    // Các món đã được quét SKU đối chiếu, phục vụ phiếu gom hàng DG-01
    private List<String> scannedProductIds;
    private Integer scannedCount;
    private Integer totalCount;

    // Thao tác hợp lệ ở trạng thái/tiến độ quét hiện tại: "SCAN", "CREATE_SHIPMENT"
    private List<String> allowedActions;

    // Moderator đang giữ việc đóng gói đơn này (từ QueueItem loại PACKING), null nếu chưa ai nhận
    private String claimedBy;

    // Ghi chú của người mua lúc đặt hàng
    private String buyerNote;
}
