package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CounterfeitConclusion;
import com.rehome.rehome.entity.enums.InvalidReturnChoice;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.enums.ReturnedItemAction;
import com.rehome.rehome.entity.enums.ShipBackPaymentMethod;
import com.rehome.rehome.entity.enums.ShipBackStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequest {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Đơn bị trả hàng
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Lý do trả do Member nhập
    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    // PENDING / APPROVED / SHIPPED_BACK / INSPECTING / VALID / INVALID / LOST / CANCELLED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private ReturnRequestStatus status = ReturnRequestStatus.PENDING;

    // Mã vận đơn chiều trả về kho
    @Column(name = "ghn_return_tracking_code", nullable = true, length = 255)
    private String ghnReturnTrackingCode;

    @Column(name = "ghn_fallback_used")
    @Builder.Default
    private Boolean ghnFallbackUsed = false;

    // Hạn bàn giao cho GHN, mặc định 3 ngày; không bàn giao thì hủy yêu cầu
    @Column(name = "handover_deadline", nullable = false)
    private Instant handoverDeadline;

    // SHIP_BACK / WAREHOUSE_PICKUP — chỉ có khi kết luận INVALID
    @Enumerated(EnumType.STRING)
    @Column(name = "invalid_return_choice", nullable = true)
    private InvalidReturnChoice invalidReturnChoice;

    // Hạn đến kho nhận lại hàng trả không hợp lệ, mặc định 7 ngày
    @Column(name = "pickup_deadline", nullable = true)
    private Instant pickupDeadline;

    // Kết luận xử lý của Moderator sau khi kiểm hàng về
    @Column(name = "resolution_note", nullable = true, columnDefinition = "text")
    private String resolutionNote;

    // Đúng sản phẩm gốc đã bán hay không — ghi nhận lúc Moderator kiểm hàng (F-132 / API 85)
    @Column(name = "matches_original", nullable = true)
    private Boolean matchesOriginal;

    // Hàng có bị xuống cấp (hư hỏng, thiếu phụ kiện...) so với lúc giao hay không
    @Column(name = "degraded", nullable = true)
    private Boolean degraded;

    // Ghi chú tình trạng hàng khi kiểm — bắt buộc ở API 85
    @Column(name = "condition_note", nullable = true, columnDefinition = "text")
    private String conditionNote;

    // RELIST / RETURN_TO_SELLER / DISCARD — hướng xử lý món hàng khi kết luận VALID (F-133 / API 88)
    @Enumerated(EnumType.STRING)
    @Column(name = "item_action", nullable = true)
    private ReturnedItemAction itemAction;

    // Moderator A gắn cờ nghi hàng giả — bắt buộc có Moderator thứ hai
    @Column(name = "counterfeit_suspected", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean counterfeitSuspected = false;

    // Moderator B kiểm định lại ca nghi hàng giả — F-134
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "second_moderator_id", nullable = true)
    private Account secondModerator;

    // CONFIRMED / DISMISSED — kết luận cuối về hàng giả — F-135
    @Enumerated(EnumType.STRING)
    @Column(name = "counterfeit_conclusion", nullable = true)
    private CounterfeitConclusion counterfeitConclusion;

    // Moderator xử lý yêu cầu
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "moderator_id", nullable = true)
    private Account moderator;

    // Thời điểm Member gửi yêu cầu
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Thời điểm chốt kết luận
    @Column(name = "resolved_at", nullable = true)
    private Instant resolvedAt;

    // Thời điểm Moderator giao lại hàng trả bị từ chối cho Member tại quầy — API 72
    @Column(name = "picked_up_at", nullable = true)
    private Instant pickedUpAt;

    // ---- Gửi lại hàng trả không hợp lệ qua GHN (invalidReturnChoice = SHIP_BACK) ----

    // AWAITING_PAYMENT / PAID / SHIPPING / DELIVERED / FAILED - null khi chưa chọn gửi lại
    @Enumerated(EnumType.STRING)
    @Column(name = "ship_back_status", nullable = true)
    private ShipBackStatus shipBackStatus;

    // Phí gửi lại chốt lúc chọn (GHN thật, lỗi thì phí dự phòng) - không tính lại
    @Column(name = "ship_back_fee", nullable = true, precision = 19, scale = 2)
    private BigDecimal shipBackFee;

    // Hạn trả phí gửi lại; quá hạn thì xoá lựa chọn, người mua chọn lại
    @Column(name = "ship_back_fee_deadline", nullable = true)
    private Instant shipBackFeeDeadline;

    // Địa chỉ trong sổ địa chỉ người mua chọn để nhận hàng
    @Column(name = "ship_back_address_id", nullable = true)
    private UUID shipBackAddressId;

    // Snapshot địa chỉ nhận lúc chọn - sổ địa chỉ sửa/xoá sau đó không làm lệch vận đơn
    @Column(name = "ship_back_recipient_name", nullable = true, length = 255)
    private String shipBackRecipientName;

    @Column(name = "ship_back_phone", nullable = true, length = 255)
    private String shipBackPhone;

    @Column(name = "ship_back_address_detail", nullable = true, length = 255)
    private String shipBackAddressDetail;

    @Column(name = "ship_back_ward_name", nullable = true, length = 255)
    private String shipBackWardName;

    @Column(name = "ship_back_province_name", nullable = true, length = 255)
    private String shipBackProvinceName;

    // WALLET / VNPAY
    @Enumerated(EnumType.STRING)
    @Column(name = "ship_back_payment_method", nullable = true)
    private ShipBackPaymentMethod shipBackPaymentMethod;

    @Column(name = "ship_back_paid_at", nullable = true)
    private Instant shipBackPaidAt;

    // Giao dịch VNPay đang mở cho phí gửi lại (tiền tố RSB-)
    @Column(name = "ship_back_vnp_txn_ref", nullable = true, length = 100)
    private String shipBackVnpTxnRef;

    @Column(name = "ship_back_vnp_create_date", nullable = true, length = 20)
    private String shipBackVnpCreateDate;

    // Mã vận đơn GHN chiều kho -> người mua
    @Column(name = "ship_back_tracking_code", nullable = true, length = 255)
    private String shipBackTrackingCode;

    @Column(name = "ship_back_shipped_at", nullable = true)
    private Instant shipBackShippedAt;

    @Column(name = "ship_back_delivered_at", nullable = true)
    private Instant shipBackDeliveredAt;

    @Column(name = "ship_back_failed_at", nullable = true)
    private Instant shipBackFailedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
