package com.rehome.rehome.entity.marketplace;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "product_photos", indexes = {
        @Index(name = "idx_product_photos_product_order", columnList = "product_id, display_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "is_background_removed", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isBackgroundRemoved = false;

    @Column(name = "display_order", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer displayOrder = 0;
}
