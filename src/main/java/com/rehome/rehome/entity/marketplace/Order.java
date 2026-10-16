package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
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

    // Địa chỉ giao hàng chọn lúc đặt
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipping_address_id", nullable = false)
    private ShippingAddress shippingAddress;

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

    // Hạn thanh toán, mặc định 15 phút; quá hạn hủy đơn và nhả hàng về sàn
    @Column(name = "payment_deadline", nullable = false)
    private Instant paymentDeadline;

    // Thời điểm GHN báo đã giao
    @Column(name = "delivered_at", nullable = true)
    private Instant deliveredAt;

    // Hạn tự hoàn thành, mặc định 3 ngày sau khi giao
    @Column(name = "auto_complete_deadline", nullable = true)
    private Instant autoCompleteDeadline;

    // 1–5 sao — UC-39 / F-066
    @Column(name = "service_rating", nullable = true)
    private Short serviceRating;

    // Thời điểm Member đánh giá
    @Column(name = "rated_at", nullable = true)
    private Instant ratedAt;

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
