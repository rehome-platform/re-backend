package com.rehome.rehome.entity.tryon;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.TryOnModelType;
import com.rehome.rehome.entity.marketplace.Product;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "try_on_images", indexes = @Index(name = "idx_try_on_reuse", columnList = "member_id,generation_fingerprint,created_at"))
@Check(constraints = "(top_product_id is not null and bottom_product_id is not null and dress_product_id is null) or (top_product_id is null and bottom_product_id is null and dress_product_id is not null)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TryOnImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false)
    private TryOnModelType modelType;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "top_product_id", nullable = true)
    private Product topProduct;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "bottom_product_id", nullable = true)
    private Product bottomProduct;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "dress_product_id", nullable = true)
    private Product dressProduct;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "generation_fingerprint", length = 64)
    private String generationFingerprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_asset_fingerprint")
    private TryOnSharedAsset sharedAsset;

    @Builder.Default
    @org.hibernate.annotations.ColumnDefault("true")
    @Column(name = "quota_charged", nullable = false)
    private boolean quotaCharged = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
