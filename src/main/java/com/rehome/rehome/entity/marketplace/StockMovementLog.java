package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.StockMovementType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "stock_movement_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementLog {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Sản phẩm có biến động
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Kho xảy ra biến động
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    // STOCK_IN / SHELF_MOVE / WITHDRAWAL_OUT / RETURN_IN / DONATED_OUT / DESTROYED
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private StockMovementType movementType;

    // Vị trí kệ trước khi chuyển
    @Column(name = "from_location", nullable = true, length = 255)
    private String fromLocation;

    // Vị trí kệ sau khi chuyển
    @Column(name = "to_location", nullable = true, length = 255)
    private String toLocation;

    // Ghi chú thêm của Moderator
    @Column(name = "note", nullable = true, columnDefinition = "text")
    private String note;

    // Moderator thực hiện
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moderator_id", nullable = false)
    private Account moderator;

    // Thời điểm ghi nhận — F-126
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
