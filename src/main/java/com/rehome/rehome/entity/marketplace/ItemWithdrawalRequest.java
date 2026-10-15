package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.WithdrawalFeePaymentMethod;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "item_withdrawal_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemWithdrawalRequest {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Sản phẩm Member muốn rút về
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Người ký gửi
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    // Phí rút hàng; bằng 0 nếu rút trong cửa sổ 7 ngày miễn phí
    @Column(name = "fee_charged", nullable = false, precision = 19, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal feeCharged = new BigDecimal("0");

    // WALLET / MOMO / MIXED — trừ ví trước, thiếu mới qua MoMo
    @Enumerated(EnumType.STRING)
    @Column(name = "fee_payment_method", nullable = true)
    private WithdrawalFeePaymentMethod feePaymentMethod;

    // PENDING_PICKUP / COMPLETED / EXPIRED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING_PICKUP'")
    @Builder.Default
    private ItemWithdrawalStatus status = ItemWithdrawalStatus.PENDING_PICKUP;

    // Hạn đến kho nhận hàng, mặc định 7 ngày; quá hạn hủy và đăng bán lại
    @Column(name = "pickup_deadline", nullable = false)
    private Instant pickupDeadline;

    // Thời điểm tạo yêu cầu
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Thời điểm Moderator bàn giao hàng tại quầy
    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
