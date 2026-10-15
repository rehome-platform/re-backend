package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.DegradedItemChoice;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "degraded_item_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DegradedItemCase {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Sản phẩm về kho trong tình trạng xấu hơn
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Yêu cầu trả hàng dẫn đến ca này, nếu có
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "return_request_id", nullable = true, unique = true)
    private ReturnRequest returnRequest;

    // FULL_COMPENSATION / PARTIAL_WITH_ITEM_BACK — Member chọn
    @Enumerated(EnumType.STRING)
    @Column(name = "choice", nullable = false)
    private DegradedItemChoice choice;

    // Tiền nền tảng đền; đền đủ hoặc 50% tùy lựa chọn — F-162
    @Column(name = "compensation_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal compensationAmount;

    // Moderator kết luận hàng xuống cấp
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moderator_id", nullable = false)
    private Account moderator;

    // Thời điểm ghi nhận ca
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
