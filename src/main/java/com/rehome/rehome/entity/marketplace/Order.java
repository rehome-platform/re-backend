package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CancelReason;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.OrderStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    // Khóa chính của đơn
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Mã đơn Member nhìn thấy và dùng để tra cứu
    @Column(name = "order_no", nullable = false, unique = true, length = 255)
    private String orderNo;

    // Người mua
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Account buyer;

    // Mỗi đơn chỉ 1 kho — giỏ nhiều kho bị tách thành nhiều đơn
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    // Địa chỉ giao hàng chọn lúc đặt — chỉ để tham chiếu, không dùng để hiển thị
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipping_address_id", nullable = false)
    private ShippingAddress shippingAddress;

    // Bản chụp lúc đặt hàng, không đổi theo khi Member sửa/xóa địa chỉ sau này
    @Column(name = "shipping_recipient_name", nullable = true, length = 255)
    private String shippingRecipientName;

    @Column(name = "shipping_phone", nullable = true, length = 255)
    private String shippingPhone;

    @Column(name = "shipping_address_detail", nullable = true, length = 255)
    private String shippingAddressDetail;

    // Bản chụp phường/xã/tỉnh lúc đặt hàng, dùng để tạo vận đơn GHN - F-127
    @Column(name = "shipping_ward_name", nullable = true, length = 255)
    private String shippingWardName;

    @Column(name = "shipping_district_name", nullable = true, length = 255)
    private String shippingDistrictName;

    @Column(name = "shipping_province_name", nullable = true, length = 255)
    private String shippingProvinceName;

    @Column(name = "shipping_is_new_address", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean shippingIsNewAddress = false;

    // Số món đã quét SKU đối chiếu trước khi tạo vận đơn, so với OrderItem - F-127
    @Column(name = "scanned_count", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer scannedCount = 0;

    // Tổng tiền sản phẩm, chưa gồm phí ship
    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    // Phí vận chuyển, trả hết cho GHN, nền tảng không hưởng phần này
    @Column(name = "shipping_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingFee;

    // Số tiền người mua trả = subtotal + shipping_fee
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    // WALLET / MOMO — chọn một, không trả nửa ví nửa MoMo
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private OrderPaymentMethod paymentMethod;

    // PENDING_PAYMENT / PAID / PACKING / SHIPPING / DELIVERED / COMPLETED / CANCELLED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING_PAYMENT'")
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    // Mã vận đơn GHN, có sau khi kho đóng gói
    @Column(name = "ghn_tracking_code", nullable = true, length = 255)
    private String ghnTrackingCode;

    // Ngày giao dự kiến GHN trả về lúc tạo vận đơn (expected_delivery_time), chụp lại để màn
    // theo dõi vận chuyển hiện được mà không phải gọi lại GHN
    @Column(name = "expected_delivery_date", nullable = true)
    private Instant expectedDeliveryDate;

    // Hạn thanh toán, mặc định 15 phút; quá hạn hủy đơn và nhả hàng về sàn
    @Column(name = "payment_deadline", nullable = false)
    private Instant paymentDeadline;

    // Thời điểm GHN báo đã giao
    @Column(name = "delivered_at", nullable = true)
    private Instant deliveredAt;

    // Hạn tự hoàn thành, mặc định 3 ngày sau khi giao
    @Column(name = "auto_complete_deadline", nullable = true)
    private Instant autoCompleteDeadline;

    // Lý do hủy — chỉ có PAYMENT_EXPIRED được code hủy tự động gán hiện tại
    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", nullable = true)
    private CancelReason cancelReason;

    // Thời điểm đơn chuyển sang CANCELLED
    @Column(name = "cancelled_at", nullable = true)
    private Instant cancelledAt;

    // Số tiền đã hoàn vào ví người mua khi hủy đơn — F-061
    @Column(name = "refund_amount", nullable = true, precision = 19, scale = 2)
    private BigDecimal refundAmount;

    // Thời điểm đơn chuyển sang COMPLETED — F-063/F-064
    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    // Thời điểm tiền thực sự vào ví người ký gửi. Null = chưa trả. Tách khỏi completedAt vì
    // tiền giữ lại tới hết hạn trả hàng (autoCompleteDeadline) rồi mới cộng ví, không trả ngay
    // lúc confirm-receipt nữa — xem OrderAutoCompleteServiceImpl.
    @Column(name = "payout_at", nullable = true)
    private Instant payoutAt;

    // 1–5 sao — UC-39 / F-066
    @Column(name = "service_rating", nullable = true)
    private Short serviceRating;

    // Thời điểm Member đánh giá
    @Column(name = "rated_at", nullable = true)
    private Instant ratedAt;

    // Nhận xét của Member khi đánh giá — F-066
    @Column(name = "rating_comment", nullable = true, columnDefinition = "text")
    private String ratingComment;

    // Thời điểm đặt đơn — mốc chốt tỷ lệ hoa hồng theo kỳ
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Thời điểm cập nhật gần nhất
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}
