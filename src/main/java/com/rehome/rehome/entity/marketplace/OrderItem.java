package com.rehome.rehome.entity.marketplace;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order", columnList = "order_id"),
        @Index(name = "idx_order_items_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "price_at_order", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceAtOrder;

    @Column(name = "commission_rate_applied", nullable = false, precision = 10, scale = 6)
    private BigDecimal commissionRateApplied;

    @Column(name = "tax_rate_applied", nullable = false, precision = 10, scale = 6)
    private BigDecimal taxRateApplied;

    @Column(name = "term_applied", nullable = true)
    private Short termApplied;

    @Column(name = "scanned", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean scanned = false;
}
