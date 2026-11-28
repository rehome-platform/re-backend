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
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private ReturnRequestStatus status = ReturnRequestStatus.PENDING;

    @Column(name = "ghn_return_tracking_code", nullable = true, length = 255)
    private String ghnReturnTrackingCode;

    @Column(name = "ghn_fallback_used")
    @Builder.Default
    private Boolean ghnFallbackUsed = false;

    @Column(name = "handover_deadline", nullable = false)
    private Instant handoverDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "invalid_return_choice", nullable = true)
    private InvalidReturnChoice invalidReturnChoice;

    @Column(name = "pickup_deadline", nullable = true)
    private Instant pickupDeadline;

    @Column(name = "resolution_note", nullable = true, columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "matches_original", nullable = true)
    private Boolean matchesOriginal;

    @Column(name = "degraded", nullable = true)
    private Boolean degraded;

    @Column(name = "condition_note", nullable = true, columnDefinition = "text")
    private String conditionNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_action", nullable = true)
    private ReturnedItemAction itemAction;

    @Column(name = "counterfeit_suspected", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean counterfeitSuspected = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "second_moderator_id", nullable = true)
    private Account secondModerator;

    @Enumerated(EnumType.STRING)
    @Column(name = "counterfeit_conclusion", nullable = true)
    private CounterfeitConclusion counterfeitConclusion;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "moderator_id", nullable = true)
    private Account moderator;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at", nullable = true)
    private Instant resolvedAt;

    @Column(name = "picked_up_at", nullable = true)
    private Instant pickedUpAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ship_back_status", nullable = true)
    private ShipBackStatus shipBackStatus;

    @Column(name = "ship_back_fee", nullable = true, precision = 19, scale = 2)
    private BigDecimal shipBackFee;

    @Column(name = "ship_back_fee_deadline", nullable = true)
    private Instant shipBackFeeDeadline;

    @Column(name = "ship_back_address_id", nullable = true)
    private UUID shipBackAddressId;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "ship_back_payment_method", nullable = true)
    private ShipBackPaymentMethod shipBackPaymentMethod;

    @Column(name = "ship_back_paid_at", nullable = true)
    private Instant shipBackPaidAt;

    @Column(name = "ship_back_vnp_txn_ref", nullable = true, length = 100)
    private String shipBackVnpTxnRef;

    @Column(name = "ship_back_vnp_create_date", nullable = true, length = 20)
    private String shipBackVnpCreateDate;

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
