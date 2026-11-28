package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
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
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    @Column(name = "fee_charged", nullable = false, precision = 19, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal feeCharged = new BigDecimal("0");

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_payment_method", nullable = true)
    private WithdrawalFeePaymentMethod feePaymentMethod;

    @Column(name = "fee_paid_at", nullable = true)
    private Instant feePaidAt;

    @Column(name = "fee_payment_deadline", nullable = true)
    private Instant feePaymentDeadline;

    @Column(name = "vnp_txn_ref", nullable = true, length = 255)
    private String vnpTxnRef;

    @Column(name = "vnp_transaction_no", nullable = true, length = 255)
    private String vnpTransactionNo;

    @Column(name = "vnp_create_date", nullable = true, length = 32)
    private String vnpCreateDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING_APPROVAL'")
    @Builder.Default
    private ItemWithdrawalStatus status = ItemWithdrawalStatus.PENDING_APPROVAL;

    @Column(name = "pickup_deadline", nullable = true)
    private Instant pickupDeadline;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "moderator_id", nullable = true)
    private Account moderator;

    @Column(name = "reject_reason", nullable = true, columnDefinition = "text")
    private String rejectReason;

    @Column(name = "reviewed_at", nullable = true)
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private long version;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
