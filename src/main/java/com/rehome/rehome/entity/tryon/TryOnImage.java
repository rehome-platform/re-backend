package com.rehome.rehome.entity.tryon;

import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.enums.TryOnModelType;
import com.rehome.rehome.entity.marketplace.Product;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "try_on_images")
@Check(constraints = "(top_product_id is not null and bottom_product_id is not null and dress_product_id is null) or (top_product_id is null and bottom_product_id is null and dress_product_id is not null)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TryOnImage {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Chủ bộ sưu tập ảnh
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    // MALE / FEMALE — người mẫu Member chọn
    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false)
    private TryOnModelType modelType;

    // Món ở slot Áo. Bắt buộc nếu thử kiểu Áo+Quần; để NULL nếu thử Đầm (dress_product_id)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "top_product_id", nullable = true)
    private Product topProduct;

    // Món ở slot Quần. Bắt buộc nếu thử kiểu Áo+Quần; để NULL nếu thử Đầm (dress_product_id)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "bottom_product_id", nullable = true)
    private Product bottomProduct;

    // Món ở slot Đầm (thử outfit 1 món). Bắt buộc nếu không dùng Áo+Quần
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "dress_product_id", nullable = true)
    private Product dressProduct;

    // Ảnh thử đồ do mô hình AI sinh ra
    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    // Thời điểm sinh ảnh
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
