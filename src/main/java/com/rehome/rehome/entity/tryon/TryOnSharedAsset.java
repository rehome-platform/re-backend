package com.rehome.rehome.entity.tryon;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/** Public mannequin result, independent of any member's private collection. */
@Entity
@Table(name = "try_on_shared_assets")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TryOnSharedAsset {
    @Id
    @Column(name = "fingerprint", length = 64, nullable = false)
    private String fingerprint;

    @Column(name = "image_url", length = 2048, nullable = false)
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
