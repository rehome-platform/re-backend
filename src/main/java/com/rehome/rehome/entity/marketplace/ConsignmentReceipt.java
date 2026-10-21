    package com.rehome.rehome.entity.marketplace;

    import com.rehome.rehome.entity.auth.Account;
    import jakarta.persistence.*;
    import java.math.BigDecimal;
    import java.time.Instant;
    import java.util.UUID;
    import lombok.*;
    import org.hibernate.annotations.ColumnDefault;

    @Entity
    @Table(name = "consignment_receipts")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public class ConsignmentReceipt {
        // Khóa chính của biên nhận
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(name = "id", nullable = false)
        private UUID id;

        // Số biên nhận in trên PDF, ví dụ KG-2026-000123
        @Column(name = "receipt_no", nullable = false, unique = true, length = 255)
        private String receiptNo;

        // Form đã dẫn đến biên nhận này
        @OneToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "consignment_form_id", nullable = false, unique = true)
        private ConsignmentForm consignmentForm;

        // Bên B, người ký gửi
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "member_id", nullable = false)
        private Account member;

        // Bên A, Moderator tiếp nhận tại quầy
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "moderator_id", nullable = false)
        private Account moderator;

        // Kho nhận hàng
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "warehouse_id", nullable = false)
        private Warehouse warehouse;

        // Giá bán hai bên chốt; snapshot, đổi cấu hình sau không ảnh hưởng
        @Column(name = "agreed_price", nullable = false, precision = 19, scale = 2)
        private BigDecimal agreedPrice;

        // Giá sàn = 50% agreed_price; Member không được hạ thấp hơn mức này
        @Column(name = "floor_price", nullable = false, precision = 19, scale = 2)
        private BigDecimal floorPrice;

        // Độ dài một kỳ ký gửi; snapshot
        @Column(name = "term_length_days", nullable = false)
        @ColumnDefault("30")
        @Builder.Default
        private Integer termLengthDays = 30;

        // Số lần tự gia hạn, 2 lần = tổng 90 ngày trên sàn; snapshot
        @Column(name = "max_renewals", nullable = false)
        @ColumnDefault("2")
        @Builder.Default
        private Integer maxRenewals = 2;

        // Số ngày đầu mỗi kỳ được rút hàng miễn phí; snapshot
        @Column(name = "free_withdrawal_window_days", nullable = false)
        @ColumnDefault("7")
        @Builder.Default
        private Integer freeWithdrawalWindowDays = 7;

        // Hoa hồng kỳ 1 (ngày 0–30); snapshot
        @Column(name = "commission_rate_term1", nullable = false, precision = 10, scale = 6)
        @ColumnDefault("0.10")
        @Builder.Default
        private BigDecimal commissionRateTerm1 = new BigDecimal("0.10");

        // Hoa hồng kỳ 2 (ngày 31–60); snapshot
        @Column(name = "commission_rate_term2", nullable = false, precision = 10, scale = 6)
        @ColumnDefault("0.15")
        @Builder.Default
        private BigDecimal commissionRateTerm2 = new BigDecimal("0.15");

        // Hoa hồng kỳ 3 (ngày 61–90); snapshot
        @Column(name = "commission_rate_term3", nullable = false, precision = 10, scale = 6)
        @ColumnDefault("0.20")
        @Builder.Default
        private BigDecimal commissionRateTerm3 = new BigDecimal("0.20");

        // Phí rút lại hàng ngoài cửa sổ miễn phí; snapshot
        @Column(name = "withdrawal_fee", nullable = false, precision = 19, scale = 2)
        private BigDecimal withdrawalFee;

        // Tỷ lệ đền khi Member lấy hàng xuống cấp về; snapshot
        @Column(name = "degraded_compensation_rate", nullable = false, precision = 10, scale = 6)
        @ColumnDefault("0.50")
        @Builder.Default
        private BigDecimal degradedCompensationRate = new BigDecimal("0.50");

        // Lúc Member bấm xác nhận — biên nhận có hiệu lực từ mốc này
        @Column(name = "confirmed_at")
        private Instant confirmedAt;

        // Tệp PDF hệ thống sinh ra — F-022
        @Column(name = "pdf_url", nullable = false, length = 2048)
        private String pdfUrl;

        // Thời điểm tạo bản ghi
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        private void onCreate() {
            Instant now = Instant.now();
            if (createdAt == null) createdAt = now;
        }
        // Tình trạng theo kết quả kiểm định của Moderator (API 34), API 37 copy sang Product
        @Column(name = "condition_note", nullable = true, columnDefinition = "text")
        private String conditionNote;

    }
