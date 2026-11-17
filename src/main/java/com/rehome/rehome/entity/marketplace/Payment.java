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

    // Số tiền thực nhận qua VNPay
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // VNPAY; trả bằng ví không tạo dòng ở bảng này
    @Column(name = "method", nullable = false, length = 255)
    @ColumnDefault("'VNPAY'")
    @Builder.Default
    private String method = "VNPAY";

    // vnp_TxnRef gửi cho VNPay lúc tạo giao dịch, dùng map IPN/return về đúng Payment.
    // Không unique: một giao dịch VNPay có thể trả cho nhiều đơn cùng lúc (giỏ nhiều kho),
    // khi đó mỗi đơn vẫn có một dòng payments riêng nhưng chung vnp_txn_ref.
    @Column(name = "vnp_txn_ref", nullable = true, length = 255)
    private String vnpTxnRef;

    // Mã giao dịch VNPay (vnp_TransactionNo) trả về khi thanh toán xong
    @Column(name = "vnp_transaction_no", nullable = true, length = 255)
    private String vnpTransactionNo;

    // vnp_CreateDate (yyyyMMddHHmmss) lúc tạo giao dịch - VNPay yêu cầu gửi lại giá trị này khi tra cứu (querydr)
    @Column(name = "vnp_create_date", nullable = true, length = 32)
    private String vnpCreateDate;

    // PENDING / SUCCESS / FAILED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    // Thời điểm VNPay xác nhận đã nhận tiền
    @Column(name = "paid_at", nullable = true)
    private Instant paidAt;
}
