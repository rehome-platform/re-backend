package com.rehome.rehome.entity.marketplace;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(name = "uk_cart_item_pair", columnNames = {"cart_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Giỏ chứa dòng này
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    // Sản phẩm trong giỏ; UNIQUE cùng cart_id — mỗi món chỉ một dòng
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Thời điểm thêm vào giỏ
    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (addedAt == null) addedAt = now;
    }
}
