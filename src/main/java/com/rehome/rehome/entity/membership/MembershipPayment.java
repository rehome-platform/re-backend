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

@Entity
@Table(name = "membership_payments", indexes = {
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

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 255)
    private OrderPaymentMethod method;

    @Column(name = "vnp_txn_ref", nullable = true, length = 255)
    private String vnpTxnRef;

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
