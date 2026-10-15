package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.enums.TransactionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "payout_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutRequest {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Ví bị trừ tiền
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    // Số tiền rút; phải đạt mức rút tối thiểu Admin cấu hình
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // PENDING / PROCESSING / SUCCESS / FAILED — không có bước người duyệt
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    // Lý do thất bại, để nhả lại tiền đã khóa cho Member tạo lại
    @Column(name = "failure_reason", nullable = true, length = 255)
    private String failureReason;

    // Thời điểm tạo yêu cầu, cũng là lúc khóa số dư
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Thời điểm giả lập chi tiền xong
    @Column(name = "processed_at", nullable = true)
    private Instant processedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
