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
        // Khóa chính của biên nhận
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(name = "id", nullable = false)
        private UUID id;

        // Số biên nhận in trên PDF, ví dụ KG-2026-000123
        @Column(name = "receipt_no", nullable = false, unique = true, length = 255)
        private String receiptNo;

        // Form đã dẫn đến biên nhận này. Một form có thể có nhiều biên nhận: lần đầu và các lần ký gửi lại cuối kỳ
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "consignment_form_id", nullable = false)
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

        // Bản đã render của các Điều khoản dạng đoạn văn (Điều 4,5,6,7,9,10), chụp lúc TẠO biên
        // nhận từ ReceiptClauseTemplate đang bật (xem ReceiptClauseRenderer). Admin sửa/xoá
        // template sau đó không ảnh hưởng gì ở đây — chỉ áp dụng cho biên nhận tạo mới.
        //
        // KHÔNG ép columnDefinition = "jsonb": để Hibernate tự chọn kiểu cột theo đúng dialect -
        // Postgres (thật) ra "jsonb", H2 (test) ra "json" (H2 không nhận diện "jsonb" trong DDL,
        // ép cứng chuỗi đó làm CREATE TABLE lỗi "Unknown data type: JSONB" trên mọi test có insert
        // ConsignmentReceipt thật, ví dụ SoldOrderQueryTest).
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "clauses_snapshot", nullable = true)
        private JsonNode clausesSnapshot;

        // PDF F-022 nằm ở bảng riêng consignment_receipt_pdfs (xem ConsignmentReceiptPdf),
        // tải qua GET /consignment-receipts/{id}/pdf chứ không có URL công khai.

        // Thời điểm tạo bản ghi
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        private void onCreate() {
            Instant now = Instant.now();
            if (createdAt == null) createdAt = now;
            // Không @Builder.Default: effectiveStatus() phải tự suy ra PENDING/CONFIRMED từ confirmedAt
            // khi status còn null (object dựng tay trong test, hoặc hàng cũ trước khi có cột này) -
            // chỉ ép giá trị cụ thể lúc thật sự lưu xuống DB để thoả NOT NULL.
            if (status == null) status = effectiveStatus();
        }
        // Tình trạng theo kết quả kiểm định của Moderator (API 34), API 37 copy sang Product
        @Column(name = "condition_note", nullable = true, columnDefinition = "text")
        private String conditionNote;

        // Ghi chú nội bộ của Moderator (khác conditionNote là tình trạng hàng)
        @Column(name = "moderator_note", nullable = true, columnDefinition = "text")
        private String moderatorNote;

        // Trạng thái biên nhận: PENDING (chưa Member xác nhận) / CONFIRMED / VOIDED (Moderator huỷ,
        // dữ liệu sai trước khi nhập kho). Cột cũ có thể còn NULL ở các hàng tạo trước khi thêm cột
        // này - dùng effectiveStatus() để suy ra chứ không đọc trực tiếp status ở nơi khác.
        @Column(name = "status")
        @ColumnDefault("'PENDING'")
        private ReceiptStatus status;

        // Số phiên bản, tăng mỗi lần Moderator sửa (editReceipt) trước khi Member xác nhận - dùng để
        // chặn Member xác nhận nhầm bản cũ nếu đang xem biên nhận trên một tab khác lúc Moderator sửa
        @Column(name = "version", nullable = false)
        @ColumnDefault("1")
        @Builder.Default
        private Integer version = 1;

        // Lý do Moderator sửa biên nhận lần gần nhất (editReceipt), null nếu chưa sửa bao giờ
        @Column(name = "edit_reason", columnDefinition = "text")
        private String editReason;

        // Lúc sửa biên nhận lần gần nhất
        @Column(name = "edited_at")
        private Instant editedAt;

        // Lý do Moderator huỷ biên nhận (voidReceipt)
        @Column(name = "void_reason", columnDefinition = "text")
        private String voidReason;

        // Lúc biên nhận bị huỷ
        @Column(name = "voided_at")
        private Instant voidedAt;

        // Moderator đã huỷ biên nhận
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "voided_by")
        private Account voidedBy;

        // Biên nhận đã bị huỷ mà biên nhận này thay thế (ký gửi lại sau khi huỷ); null nếu đây là
        // biên nhận đầu tiên của form. Mỗi biên nhận đã huỷ chỉ được thay thế một lần (unique index
        // uq_receipt_replacement trên previous_receipt_id).
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "previous_receipt_id")
        private ConsignmentReceipt previousReceipt;

        // Trạng thái hiệu lực: ưu tiên cột status; hàng cũ (tạo trước khi thêm cột) chưa có giá trị
        // thì suy ra từ confirmedAt, giữ đúng quy tắc backfill ở scripts/2026-11-18_receipt_edit_and_void.sql
        public ReceiptStatus effectiveStatus() {
            if (status != null) {
                return status;
            }
            return confirmedAt == null ? ReceiptStatus.PENDING : ReceiptStatus.CONFIRMED;
        }

        // Số biên nhận thay thế chỉ có đuôi "-NN" (đúng 2 chữ số) khi đã qua ít nhất một lần thay thế -
        // số biên nhận gốc luôn tận cùng bằng 6 chữ số liền sau dấu "-" của năm (KG-2026-000123) nên
        // không bị nhận lầm thành đuôi thay thế.
        private static final java.util.regex.Pattern REPLACEMENT_SUFFIX =
                java.util.regex.Pattern.compile("^(.*)-(\\d{2})$");

        // Liên kết biên nhận này là bản ký gửi lại thay cho biên nhận đã huỷ `previous`: giữ nguyên
        // các điều khoản/hoa hồng đã thoả thuận ở biên nhận gốc (chỉ agreedPrice/floorPrice/ghi chú là
        // chốt lại mới) và đánh số theo cùng gốc biên nhận gốc, tăng dần "-02", "-03"... để các biên
        // nhận trong cùng một chuỗi thay thế dễ truy ngược nguồn gốc.
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
