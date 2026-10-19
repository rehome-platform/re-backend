package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Đơn được thanh toán
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    // Số tiền thực nhận qua MoMo
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Luôn là MOMO; trả bằng ví không tạo dòng ở bảng này
    @Column(name = "method", nullable = false, length = 255)
    @ColumnDefault("'MOMO'")
    @Builder.Default
    private String method = "MOMO";

    // Mã giao dịch MoMo trả về qua webhook
    @Column(name = "momo_transaction_id", nullable = true, length = 255)
    private String momoTransactionId;

    // orderId gửi cho MoMo lúc tạo giao dịch (khác order_id nội bộ, dùng để map IPN về đúng Payment)
    @Column(name = "momo_order_id", nullable = true, length = 255, unique = true)
    private String momoOrderId;

    // requestId gửi cho MoMo lúc tạo giao dịch
    @Column(name = "momo_request_id", nullable = true, length = 255)
    private String momoRequestId;

    // PENDING / SUCCESS / FAILED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    // Thời điểm MoMo xác nhận đã nhận tiền
    @Column(name = "paid_at", nullable = true)
    private Instant paidAt;
}
