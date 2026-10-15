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
    // Khóa chính của một bút toán
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // MEMBER / PLATFORM — quyết định wallet_id trỏ vào bảng nào
    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", nullable = false)
    private WalletType walletType;

    // Khóa đa hình: Wallet.id nếu MEMBER, PlatformFund.id nếu PLATFORM — không đặt FK được vì trỏ hai bảng
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    // ORDER_PAYMENT / ORDER_PAYOUT / REFUND / WITHDRAWAL / WITHDRAWAL_FEE / DEGRADED_COMPENSATION / COMMISSION_REVENUE / WITHDRAWAL_FEE_REVENUE
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private WalletTransactionType type;

    // Số tiền; dương là tiền vào, âm là tiền ra
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Đơn liên quan, để Member xem doanh thu theo từng đơn
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_order_id", nullable = true)
    private Order relatedOrder;

    // Bút toán đối ứng bên kia — ghi sổ kép, hai dòng bằng nhau và ngược dấu
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "counterpart_transaction_id", nullable = true)
    private WalletTransaction counterpartTransaction;

    // PENDING / PROCESSING / SUCCESS / FAILED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'SUCCESS'")
    @Builder.Default
    private TransactionStatus status = TransactionStatus.SUCCESS;

    // Diễn giải ngắn hiển thị trong lịch sử ví
    @Column(name = "note", nullable = true, length = 255)
    private String note;

    // Thời điểm ghi bút toán — F-039
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
