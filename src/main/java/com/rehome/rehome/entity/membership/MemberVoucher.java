package com.rehome.rehome.entity.membership;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * Một mã cụ thể trong ví voucher của một Member. <b>Snapshot luật</b> từ template lúc phát, nên
 * Admin sửa template về sau không đổi mã đã phát.
 *
 * <p><b>Cố ý KHÔNG có {@code used_order_id}.</b> Một mã áp cho CẢ LẦN THANH TOÁN và có thể trải
 * trên nhiều đơn (giỏ nhiều món bị tách thành nhiều đơn), nên quan hệ ngược nằm ở
 * {@code orders.voucher_id}. Muốn biết mã đã dùng cho đơn nào thì query
 * {@code orders WHERE voucher_id = ?} ({@code OrderRepository.findAllByVoucher_Id}).
 */
@Entity
@Table(name = "member_vouchers", indexes = {
        @Index(name = "idx_member_vouchers_member_status_expires",
                columnList = "member_id, status, expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Mã Member nhập lúc đặt hàng, ví dụ SHIP-7K2M9A. UNIQUE toàn hệ thống.
    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    // Để thống kê theo mẫu (W-05)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_template_id", nullable = false)
    private VoucherTemplate voucherTemplate;

    // Subscription nào phát ra mã này. null với mã do Admin phát tay (nếu sau này có).
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "source_subscription_id", nullable = true)
    private MemberSubscription sourceSubscription;

    // ---- 5 cột dưới đây là SNAPSHOT từ template ----

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 255)
    private VoucherType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_kind", nullable = false, length = 255)
    private VoucherDiscountKind discountKind;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount_amount", nullable = true, precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_order_amount", nullable = false, precision = 19, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 255)
    @ColumnDefault("'UNUSED'")
    @Builder.Default
    private MemberVoucherStatus status = MemberVoucherStatus.UNUSED;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    // issued_at + template.valid_days
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at", nullable = true)
    private Instant usedAt;

    @PrePersist
    private void onCreate() {
        if (issuedAt == null) issuedAt = Instant.now();
    }
}
