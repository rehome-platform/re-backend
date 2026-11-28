package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.util.VietnameseText;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Campaign {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationProfile organization;

    @Column(name = "title", nullable = true, length = 255)
    private String title;

    @Column(name = "title_normalized", nullable = true, length = 255)
    private String titleNormalized;

    @Column(name = "cover_url", nullable = true, length = 2048)
    private String coverUrl;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "receive_at_own_address", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean receiveAtOwnAddress = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "campaign_warehouses",
            joinColumns = @JoinColumn(name = "campaign_id"),
            inverseJoinColumns = @JoinColumn(name = "warehouse_id"))
    @Builder.Default
    private Set<Warehouse> warehouses = new HashSet<>();

    @Column(name = "location_name", nullable = true, length = 255)
    private String locationName;

    @Column(name = "latitude", nullable = true, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = true, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "preferred_item_types", nullable = true, length = 255)
    private String preferredItemTypes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'OPEN'")
    @Builder.Default
    private CampaignStatus status = CampaignStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_organization_ban")
    private CampaignStatus statusBeforeOrganizationBan;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        titleNormalized = VietnameseText.normalize(title);
    }

    @PreUpdate
    private void onUpdate() {
        titleNormalized = VietnameseText.normalize(title);
    }
}
