package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.OrganizationProfile;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "donation_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonationNote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationProfile organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "campaign_id", nullable = true)
    private Campaign campaign;

    @Column(name = "donor_name", nullable = true, length = 255)
    private String donorName;

    @Column(name = "is_anonymous", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isAnonymous = false;

    @Column(name = "note", nullable = true, columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
