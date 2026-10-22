package com.rehome.rehome.entity.marketplace;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "product_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPhoto {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Sản phẩm của ảnh này
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Ảnh sản phẩm hiển thị trên sàn
    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    // Đã tách nền chưa; chỉ ảnh đã tách nền dùng được cho thử đồ AI — F-120
    @Column(name = "is_background_removed", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isBackgroundRemoved = false;

    // Thứ tự hiển thị, 0 là ảnh đại diện          <-- thêm từ đây
    @Column(name = "display_order", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer displayOrder = 0;
}
