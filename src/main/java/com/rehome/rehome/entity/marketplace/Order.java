package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CancelReason;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.OrderStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "order_no", nullable = false, unique = true, length = 255)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Account buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipping_address_id", nullable = false)
    private ShippingAddress shippingAddress;

    @Column(name = "shipping_recipient_name", nullable = true, length = 255)
    private String shippingRecipientName;

    @Column(name = "shipping_phone", nullable = true, length = 255)
    private String shippingPhone;

    @Column(name = "shipping_address_detail", nullable = true, length = 255)
    private String shippingAddressDetail;

    @Column(name = "shipping_ward_name", nullable = true, length = 255)
    private String shippingWardName;

    @Column(name = "shipping_district_name", nullable = true, length = 255)
    private String shippingDistrictName;

    @Column(name = "shipping_province_name", nullable = true, length = 255)
    private String shippingProvinceName;

    @Column(name = "shipping_is_new_address", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean shippingIsNewAddress = false;

    @Column(name = "scanned_count", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer scannedCount = 0;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingFee;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "voucher_id", nullable = true)
    private com.rehome.rehome.entity.membership.MemberVoucher voucher;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private OrderPaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING_PAYMENT'")
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "ghn_tracking_code", nullable = true, length = 255)
    private String ghnTrackingCode;

    @Column(name = "expected_delivery_date", nullable = true)
    private Instant expectedDeliveryDate;

    @Column(name = "shipment_created_at", nullable = true)
    private Instant shipmentCreatedAt;

    @Column(name = "handed_over_at")
    private Instant handedOverAt;

    @Column(name = "payment_deadline", nullable = false)
    private Instant paymentDeadline;

    @Column(name = "delivered_at", nullable = true)
    private Instant deliveredAt;

    @Column(name = "auto_complete_deadline", nullable = true)
    private Instant autoCompleteDeadline;

    @Column(name = "return_deadline", nullable = true)
    private Instant returnDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", nullable = true)
    private CancelReason cancelReason;

    @Column(name = "cancelled_at", nullable = true)
    private Instant cancelledAt;

    @Column(name = "address_changed_at", nullable = true)
    private Instant addressChangedAt;

    @Column(name = "refund_amount", nullable = true, precision = 19, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    @Column(name = "payout_at", nullable = true)
    private Instant payoutAt;

    @Column(name = "service_rating", nullable = true)
    private Short serviceRating;

    @Column(name = "rated_at", nullable = true)
    private Instant ratedAt;

    @Column(name = "rating_comment", nullable = true, columnDefinition = "text")
    private String ratingComment;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_rating_image_urls", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "image_url", nullable = false, length = 2048)
    @OrderColumn(name = "position")
    @Builder.Default
    private List<String> ratingImageUrls = new ArrayList<>();

    @Column(name = "buyer_note", nullable = true, columnDefinition = "text")
    private String buyerNote;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_packing_photo_urls", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "image_url", nullable = false, length = 2048)
    @OrderColumn(name = "position")
    @Builder.Default
    private List<String> packingPhotoUrls = new ArrayList<>();

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
}
