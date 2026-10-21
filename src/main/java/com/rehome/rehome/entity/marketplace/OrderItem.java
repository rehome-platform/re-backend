package com.rehome.rehome.entity.marketplace;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Đơn chứa dòng này
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Sản phẩm được mua
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Giá bán chốt lúc đặt đơn, Member hạ giá sau không ảnh hưởng
    @Column(name = "price_at_order", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceAtOrder;

    // Tỷ lệ hoa hồng của kỳ đang chạy lúc đặt đơn, chốt tại đây
    @Column(name = "commission_rate_applied", nullable = false, precision = 10, scale = 6)
    private BigDecimal commissionRateApplied;

    // Tỷ lệ thuế khấu trừ chốt lúc đặt đơn
    @Column(name = "tax_rate_applied", nullable = false, precision = 10, scale = 6)
    private BigDecimal taxRateApplied;

    // Kỳ ký gửi (1/2/3) của sản phẩm tại lúc đặt đơn, dùng để tra cứu lại sau này
    @Column(name = "term_applied", nullable = true)
    private Short termApplied;
}
