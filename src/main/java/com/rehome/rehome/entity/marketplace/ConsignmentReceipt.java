    package com.rehome.rehome.entity.marketplace;

    import com.fasterxml.jackson.databind.JsonNode;
    import com.rehome.rehome.entity.auth.Account;
    import com.rehome.rehome.entity.enums.ReceiptStatus;
    import jakarta.persistence.*;
    import java.math.BigDecimal;
    import java.time.Instant;
    import java.util.UUID;
    import lombok.*;
    import org.hibernate.annotations.ColumnDefault;
    import org.hibernate.annotations.JdbcTypeCode;
    import org.hibernate.type.SqlTypes;

    @Entity
    @Table(name = "consignment_receipts")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public class ConsignmentReceipt {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(name = "id", nullable = false)
        private UUID id;

        @Column(name = "receipt_no", nullable = false, unique = true, length = 255)
        private String receiptNo;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "consignment_form_id", nullable = false)
        private ConsignmentForm consignmentForm;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "member_id", nullable = false)
        private Account member;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "moderator_id", nullable = false)
        private Account moderator;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "warehouse_id", nullable = false)
        private Warehouse warehouse;

        @Column(name = "agreed_price", nullable = false, precision = 19, scale = 2)
        private BigDecimal agreedPrice;

        @Column(name = "floor_price", nullable = false, precision = 19, scale = 2)
        private BigDecimal floorPrice;

        @Column(name = "term_length_days", nullable = false)
        @ColumnDefault("30")
        @Builder.Default
        private Integer termLengthDays = 30;

        @Column(name = "max_renewals", nullable = false)
        @ColumnDefault("2")
        @Builder.Default
        private Integer maxRenewals = 2;

        @Column(name = "free_withdrawal_window_days", nullable = false)
        @ColumnDefault("7")
        @Builder.Default
        private Integer freeWithdrawalWindowDays = 7;

        @Column(name = "commission_rate_term1", nullable = false, precision = 10, scale = 6)
        @ColumnDefault("0.10")
        @Builder.Default
        private BigDecimal commissionRateTerm1 = new BigDecimal("0.10");

        @Column(name = "commission_rate_term2", nullable = false, precision = 10, scale = 6)
        @ColumnDefault("0.15")
        @Builder.Default
        private BigDecimal commissionRateTerm2 = new BigDecimal("0.15");

        @Column(name = "commission_rate_term3", nullable = false, precision = 10, scale = 6)
        @ColumnDefault("0.20")
        @Builder.Default
        private BigDecimal commissionRateTerm3 = new BigDecimal("0.20");

        @Column(name = "withdrawal_fee", nullable = false, precision = 19, scale = 2)
        private BigDecimal withdrawalFee;

        @Column(name = "degraded_compensation_rate", nullable = false, precision = 10, scale = 6)
        @ColumnDefault("0.50")
        @Builder.Default
        private BigDecimal degradedCompensationRate = new BigDecimal("0.50");

        @Column(name = "confirmed_at")
        private Instant confirmedAt;

        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "clauses_snapshot", nullable = true)
        private JsonNode clausesSnapshot;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        private void onCreate() {
            Instant now = Instant.now();
            if (createdAt == null) createdAt = now;
            if (status == null) status = effectiveStatus();
        }
        @Column(name = "condition_note", nullable = true, columnDefinition = "text")
        private String conditionNote;

        @Column(name = "moderator_note", nullable = true, columnDefinition = "text")
        private String moderatorNote;

        @Enumerated(EnumType.STRING)
        @Column(name = "status")
        @ColumnDefault("'PENDING'")
        private ReceiptStatus status;

        @Column(name = "version", nullable = false)
        @ColumnDefault("1")
        @Builder.Default
        private Integer version = 1;

        @Column(name = "edit_reason", columnDefinition = "text")
        private String editReason;

        @Column(name = "edited_at")
        private Instant editedAt;

        @Column(name = "void_reason", columnDefinition = "text")
        private String voidReason;

        @Column(name = "voided_at")
        private Instant voidedAt;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "voided_by")
        private Account voidedBy;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "previous_receipt_id")
        private ConsignmentReceipt previousReceipt;

        public ReceiptStatus effectiveStatus() {
            if (status != null) {
                return status;
            }
            return confirmedAt == null ? ReceiptStatus.PENDING : ReceiptStatus.CONFIRMED;
        }

        private static final java.util.regex.Pattern REPLACEMENT_SUFFIX =
                java.util.regex.Pattern.compile("^(.*)-(\\d{2})$");

        public void replaceVoided(ConsignmentReceipt previous) {
            this.previousReceipt = previous;
            this.termLengthDays = previous.termLengthDays;
            this.maxRenewals = previous.maxRenewals;
            this.freeWithdrawalWindowDays = previous.freeWithdrawalWindowDays;
            this.commissionRateTerm1 = previous.commissionRateTerm1;
            this.commissionRateTerm2 = previous.commissionRateTerm2;
            this.commissionRateTerm3 = previous.commissionRateTerm3;
            this.withdrawalFee = previous.withdrawalFee;
            this.degradedCompensationRate = previous.degradedCompensationRate;

            java.util.regex.Matcher m = REPLACEMENT_SUFFIX.matcher(previous.receiptNo);
            this.receiptNo = m.matches()
                    ? "%s-%02d".formatted(m.group(1), Integer.parseInt(m.group(2)) + 1)
                    : previous.receiptNo + "-02";
        }
    }
