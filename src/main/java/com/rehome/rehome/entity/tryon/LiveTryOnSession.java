package com.rehome.rehome.entity.tryon;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.LiveTryOnStatus;
import com.rehome.rehome.entity.marketplace.Product;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "live_tryon_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveTryOnSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "model_id", nullable = false, length = 100)
    private String modelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private LiveTryOnStatus status = LiveTryOnStatus.STARTED;

    @Column(name = "actual_duration_seconds", nullable = true)
    private Integer actualDurationSeconds;

    @Column(name = "video_url", nullable = true, length = 2048)
    private String videoUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "first_frame_at")
    private Instant firstFrameAt;

    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
