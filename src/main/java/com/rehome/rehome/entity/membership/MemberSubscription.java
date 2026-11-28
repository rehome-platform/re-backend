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

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 255)
    private MembershipTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 255)
    private BillingCycle billingCycle;

    @Column(name = "price_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal pricePaid;

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

    @Column(name = "started_at", nullable = true)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = true)
    private Instant expiresAt;

    @Column(name = "last_voucher_issued_at", nullable = true)
    private Instant lastVoucherIssuedAt;

    @Column(name = "expiry_reminder_sent_at", nullable = true)
    private Instant expiryReminderSentAt;

    @Column(name = "cancelled_at", nullable = true)
    private Instant cancelledAt;

    @Column(name = "cancel_reason", nullable = true, length = 255)
    private String cancelReason;

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

    @Transient
    public boolean isEffectiveAt(Instant moment) {
        return status == SubscriptionStatus.ACTIVE
                && expiresAt != null
                && expiresAt.isAfter(moment);
    }
}
