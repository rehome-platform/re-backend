package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.ContentStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "community_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityPost {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationProfile organization;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "campaign_id", nullable = true)
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'VISIBLE'")
    @Builder.Default
    private ContentStatus status = ContentStatus.VISIBLE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private com.rehome.rehome.entity.enums.ContentReviewStatus reviewStatus;

    @jakarta.persistence.Version
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long version;

    @Column(columnDefinition = "text")
    private String reviewReason;

    private UUID reviewedBy;
    private Instant reviewedAt;

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
