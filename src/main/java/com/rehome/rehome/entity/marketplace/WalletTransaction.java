package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "transaction_no", nullable = true, unique = true, length = 255)
    private String transactionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", nullable = false)
    private WalletType walletType;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private WalletTransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = true, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_order_id", nullable = true)
    private Order relatedOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_order_item_id", nullable = true)
    private OrderItem relatedOrderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_payout_request_id", nullable = true)
    private PayoutRequest relatedPayoutRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_item_withdrawal_request_id", nullable = true)
    private ItemWithdrawalRequest relatedItemWithdrawalRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_degraded_item_case_id", nullable = true)
    private DegradedItemCase relatedDegradedItemCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_return_request_id", nullable = true)
    private ReturnRequest relatedReturnRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "counterpart_transaction_id", nullable = true)
    private WalletTransaction counterpartTransaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'SUCCESS'")
    @Builder.Default
    private TransactionStatus status = TransactionStatus.SUCCESS;

    @Column(name = "note", nullable = true, length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
