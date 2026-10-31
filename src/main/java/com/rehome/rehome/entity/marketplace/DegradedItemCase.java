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

    // FULL_COMPENSATION / PARTIAL_WITH_ITEM_BACK — Member chọn, null nếu chưa quyết định (API 95)
    @Enumerated(EnumType.STRING)
    @Column(name = "choice", nullable = true)
    private DegradedItemChoice choice;

    // Giá bán - hoa hồng, snapshot lúc mở ca (trước khi Member chọn) - đây là mức FULL_COMPENSATION;
    // PARTIAL_WITH_ITEM_BACK = số này x degradedCompensationRate (snapshot trên biên nhận)
    @Column(name = "compensation_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal compensationAmount;

    // Số tiền thực tế đã cộng vào ví người bán sau khi Member quyết định — F-162
    @Column(name = "paid_amount", nullable = true, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    // Moderator kết luận hàng xuống cấp
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moderator_id", nullable = false)
    private Account moderator;

    // Thời điểm ghi nhận ca
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Thời điểm Member quyết định — API 95
    @Column(name = "decided_at", nullable = true)
    private Instant decidedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
