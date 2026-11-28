package com.rehome.rehome.entity.membership;

import com.rehome.rehome.entity.enums.MembershipTier;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "membership_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, unique = true, length = 255)
    private MembershipTier tier;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @Column(name = "price_per_month", nullable = false, precision = 19, scale = 2)
    private BigDecimal pricePerMonth;

    @Column(name = "price_per_year", nullable = true, precision = 19, scale = 2)
    private BigDecimal pricePerYear;

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

    @Column(name = "purchasable", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private Boolean purchasable = true;

    @Column(name = "active", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer sortOrder = 0;

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
