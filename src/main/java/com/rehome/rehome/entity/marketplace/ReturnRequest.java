package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CounterfeitConclusion;
import com.rehome.rehome.entity.enums.InvalidReturnChoice;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import jakarta.persistence.*;
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

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
