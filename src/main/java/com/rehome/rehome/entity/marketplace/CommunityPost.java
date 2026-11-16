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
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Tổ chức đăng bài
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationProfile organization;

    // Nội dung bài viết
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    // Chiến dịch gắn kèm bài viết (OB-02/OB-03); null khi không gắn chiến dịch nào.
    // Chỉ validate "đang mở + của chính tổ chức" lúc gắn, không tự gỡ khi chiến dịch đóng sau đó.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "campaign_id", nullable = true)
    private Campaign campaign;

    // VISIBLE / REMOVED — Moderator gỡ bài thì chuyển REMOVED, không xóa dữ liệu
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'VISIBLE'")
    @Builder.Default
    private ContentStatus status = ContentStatus.VISIBLE;

    // Thời điểm đăng
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

    // Thời điểm sửa gần nhất
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
