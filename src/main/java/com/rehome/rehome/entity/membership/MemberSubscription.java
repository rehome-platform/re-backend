package com.rehome.rehome.entity.membership;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.BillingCycle;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * Một dòng = một lần Member sở hữu gói.
 *
 * <p><b>Snapshot toàn bộ hạn mức tại lúc mua.</b> Admin hạ hạn mức sau này không được ảnh hưởng
 * người đã trả tiền; ngược lại Admin TĂNG hạn mức thì service cập nhật snapshot của các dòng
 * ACTIVE ngay (xem {@code MembershipPlanAdminServiceImpl}). {@code price_paid} thì không bao giờ
 * đổi - đọc lại giá từ plan sẽ làm sai lịch sử thanh toán.
 *
 * <p><b>Ràng buộc nghiệp vụ</b>: mỗi {@code member_id} chỉ được tối đa 1 dòng {@code ACTIVE}.
 * DB không enforce bằng unique index thường được (có nhiều dòng EXPIRED cùng member), nên service
 * phải khóa dòng khi tạo - dùng {@code findActiveByMemberIdForUpdate} với PESSIMISTIC_WRITE,
 * giống cách {@code OrderServiceImpl} khóa ví bằng {@code findByMemberIdForUpdate}.
 */
@Entity
@Table(name = "member_subscriptions", indexes = {
        @Index(name = "idx_member_subscriptions_member_status", columnList = "member_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MembershipPlan plan;

    // Snapshot hạng để query/lọc không phải join sang membership_plans
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 255)
    private MembershipTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 255)
    private BillingCycle billingCycle;

    // Giá THẬT đã trả. Không đọc lại từ plan: Admin đổi giá chỉ áp cho lần mua sau.
    @Column(name = "price_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal pricePaid;

    // ---- 7 cột dưới đây đều là SNAPSHOT hạn mức từ plan lúc mua ----

    @Column(name = "live_tryon_per_day", nullable = true)
    private Integer liveTryonPerDay;

    @Column(name = "live_tryon_per_month", nullable = true)
    private Integer liveTryonPerMonth;

    @Column(name = "live_tryon_max_seconds", nullable = false)
    private Integer liveTryonMaxSeconds;

    @Column(name = "ai_tryon_per_day", nullable = true)
    private Integer aiTryonPerDay;

    @Column(name = "ai_tryon_per_month", nullable = true)
    private Integer aiTryonPerMonth;

    @Column(name = "featured_listing", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean featuredListing = false;

    @Column(name = "max_pending_consignment_forms", nullable = false)
    private Integer maxPendingConsignmentForms;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 255)
    @ColumnDefault("'PENDING_PAYMENT'")
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.PENDING_PAYMENT;

    // null khi còn PENDING_PAYMENT
    @Column(name = "started_at", nullable = true)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = true)
    private Instant expiresAt;

    // Mốc để VoucherIssueJob biết đã phát voucher của tháng nào; cũng là chốt idempotent
    @Column(name = "last_voucher_issued_at", nullable = true)
    private Instant lastVoucherIssuedAt;

    // Đã gửi thông báo nhắc gia hạn chưa - để MembershipExpiryReminderJob chỉ gửi 1 lần/subscription
    @Column(name = "expiry_reminder_sent_at", nullable = true)
    private Instant expiryReminderSentAt;

    @Column(name = "cancelled_at", nullable = true)
    private Instant cancelledAt;

    // Admin hủy thủ công thì ghi lý do
    @Column(name = "cancel_reason", nullable = true, length = 255)
    private String cancelReason;

    // Khác null = Admin tặng tay, không phải Member mua
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "granted_by_admin_id", nullable = true)
    private Account grantedByAdmin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    // "Đang có hiệu lực thật": ACTIVE và chưa quá hạn. Job chuyển EXPIRED chỉ chạy 00:05 mỗi ngày
    // nên trong khoảng giữa vẫn có dòng ACTIVE đã quá expiresAt - mọi chỗ đọc quyền lợi phải
    // dùng hàm này, không chỉ so status.
    @Transient
    public boolean isEffectiveAt(Instant moment) {
        return status == SubscriptionStatus.ACTIVE
                && expiresAt != null
                && expiresAt.isAfter(moment);
    }
}
