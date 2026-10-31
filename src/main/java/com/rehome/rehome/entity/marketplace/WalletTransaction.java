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

    // Mã hiển thị cho Member, ví dụ WT-2026-000123
    @Column(name = "transaction_no", nullable = true, unique = true, length = 255)
    private String transactionNo;

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

    // Số dư của ví ngay sau bút toán này, ghi tại lúc tạo — không suy ra bằng cách replay lịch sử
    @Column(name = "balance_after", nullable = true, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    // Đơn liên quan, để Member xem doanh thu theo từng đơn
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_order_id", nullable = true)
    private Order relatedOrder;

    // Món cụ thể liên quan — chỉ có với ORDER_PAYOUT, mỗi món một người bán khác nhau
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_order_item_id", nullable = true)
    private OrderItem relatedOrderItem;

    // Yêu cầu rút tiền sinh ra bút toán này — chỉ có với WITHDRAWAL, để Member bấm vào
    // giao dịch rút tiền trong lịch sử ví là mở được chi tiết yêu cầu rút
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_payout_request_id", nullable = true)
    private PayoutRequest relatedPayoutRequest;

    // Yêu cầu rút hàng sinh ra bút toán này — chỉ có với WITHDRAWAL_FEE (API 91 / F-033)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_item_withdrawal_request_id", nullable = true)
    private ItemWithdrawalRequest relatedItemWithdrawalRequest;

    // Ca xử lý hàng xuống cấp sinh ra bút toán này — chỉ có với DEGRADED_COMPENSATION (API 95 / F-036)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_degraded_item_case_id", nullable = true)
    private DegradedItemCase relatedDegradedItemCase;

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
