package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.DonationSource;
import com.rehome.rehome.entity.enums.DonationStatus;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "donations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Donation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "campaign_id", nullable = true)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "recorded_by_account_id", nullable = true)
    private Account recordedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    @ColumnDefault("'DIRECT'")
    @Builder.Default
    private DonationSource source = DonationSource.DIRECT;

    @Column(name = "donor_name", nullable = true, length = 255)
    private String donorName;

    @Column(name = "is_anonymous", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isAnonymous = false;

    @Column(name = "note", nullable = true, columnDefinition = "text")
    private String note;

    @ElementCollection
    @CollectionTable(name = "donation_photos", joinColumns = @JoinColumn(name = "donation_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "url", nullable = false, length = 2048)
    @Builder.Default
    private List<String> photoUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING_PICKUP'")
    @Builder.Default
    private DonationStatus status = DonationStatus.PENDING_PICKUP;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "handed_over_to_organization_id", nullable = true)
    private OrganizationProfile handedOverToOrganization;

    @Column(name = "handed_over_at", nullable = true)
    private Instant handedOverAt;

    @Column(name = "held_at")
    private Instant heldAt;

    @Column(name = "hold_reason", columnDefinition = "text")
    private String holdReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reassigned_from_campaign_id")
    private Campaign reassignedFromCampaign;

    @Column(name = "reassigned_at")
    private Instant reassignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reassigned_by")
    private Account reassignedBy;

    public String getReassignedFromCampaignTitle() {
        return reassignedFromCampaign == null ? null : reassignedFromCampaign.getTitle();
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static DonationBuilder pendingAt(Warehouse warehouse, Campaign campaign, String donorName,
                                            boolean anonymous, String note) {
        return builder().warehouse(warehouse).campaign(campaign)
                .donorName(anonymous ? null : donorName).isAnonymous(anonymous)
                .note(note).status(DonationStatus.PENDING_PICKUP);
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
