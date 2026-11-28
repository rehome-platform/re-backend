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
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "old_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal newPrice;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (changedAt == null) changedAt = now;
    }
}
