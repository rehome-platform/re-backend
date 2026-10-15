package com.rehome.rehome.entity.marketplace;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "price_change_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceChangeLog {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Sản phẩm bị đổi giá
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Giá trước khi đổi
    @Column(name = "old_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal oldPrice;

    // Giá sau khi đổi, luôn thấp hơn giá cũ
    @Column(name = "new_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal newPrice;

    // Thời điểm đổi; dùng đếm giới hạn 1 lần đổi giá mỗi ngày
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (changedAt == null) changedAt = now;
    }
}
