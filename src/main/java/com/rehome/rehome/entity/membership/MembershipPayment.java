package com.rehome.rehome.entity.membership;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * Giao dịch trả tiền mua gói. <b>Cố ý không dùng lại bảng {@code payments}</b>: cột
 * {@code payments.order_id} là NOT NULL và trỏ vào {@code orders}, mua gói thì không có đơn hàng.
 *
 * <p>⚠️ <b>Không đặt UNIQUE trên {@code momo_order_id} / {@code vnp_txn_ref}.</b> Đây là bài học
 * đã có trong repo - xem {@code scripts/drop-payment-gateway-ref-unique.sql} và ghi chú
 * §Order payment trong {@code CLAUDE.md}. Hiện mỗi subscription chỉ mở một giao dịch nên chưa có
 * hai dòng cùng ref, nhưng đặt unique rồi sẽ không drop được bằng {@code ddl-auto: update}.
 */
@Entity
@Table(name = "membership_payments", indexes = {
        @Index(name = "idx_membership_payments_momo_order_id", columnList = "momo_order_id"),
        @Index(name = "idx_membership_payments_vnp_txn_ref", columnList = "vnp_txn_ref")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private MemberSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Dùng lại enum sẵn có của đơn hàng: WALLET / MOMO / VNPAY
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 255)
    private OrderPaymentMethod method;

    @Column(name = "momo_order_id", nullable = true, length = 255)
    private String momoOrderId;

    @Column(name = "momo_request_id", nullable = true, length = 255)
    private String momoRequestId;

    @Column(name = "momo_transaction_id", nullable = true, length = 255)
    private String momoTransactionId;

    @Column(name = "vnp_txn_ref", nullable = true, length = 255)
    private String vnpTxnRef;

    // Cần cho lệnh querydr khi IPN chưa về (xem VnPayServiceImpl.syncWithVnPay)
    @Column(name = "vnp_create_date", nullable = true, length = 255)
    private String vnpCreateDate;

    @Column(name = "vnp_transaction_no", nullable = true, length = 255)
    private String vnpTransactionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 255)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "paid_at", nullable = true)
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
