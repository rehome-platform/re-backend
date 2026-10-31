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

    // WALLET / MOMO / VNPAY — chỉ set khi đã thanh toán xong (thành công), không phải lúc chọn
    @Enumerated(EnumType.STRING)
    @Column(name = "fee_payment_method", nullable = true)
    private WithdrawalFeePaymentMethod feePaymentMethod;

    // Thời điểm phí rút hàng được thanh toán xong (ví trừ ngay, hoặc MoMo/VNPay xác nhận qua IPN)
    @Column(name = "fee_paid_at", nullable = true)
    private Instant feePaidAt;

    // orderId gửi cho MoMo lúc tạo giao dịch trả phí rút hàng, dùng map IPN về đúng yêu cầu này
    @Column(name = "momo_order_id", nullable = true, length = 255)
    private String momoOrderId;

    // requestId gửi cho MoMo lúc tạo giao dịch
    @Column(name = "momo_request_id", nullable = true, length = 255)
    private String momoRequestId;

    // vnp_TxnRef gửi cho VNPay lúc tạo giao dịch, dùng map IPN về đúng yêu cầu này
    @Column(name = "vnp_txn_ref", nullable = true, length = 255)
    private String vnpTxnRef;

    // Mã giao dịch VNPay (vnp_TransactionNo) trả về khi thanh toán xong
    @Column(name = "vnp_transaction_no", nullable = true, length = 255)
    private String vnpTransactionNo;

    // vnp_CreateDate (yyyyMMddHHmmss) lúc tạo giao dịch - VNPay yêu cầu gửi lại khi tra cứu
    @Column(name = "vnp_create_date", nullable = true, length = 32)
    private String vnpCreateDate;

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
