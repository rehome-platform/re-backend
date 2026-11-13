package com.rehome.rehome.entity.membership;

import com.rehome.rehome.entity.enums.MembershipTier;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * Hạng nào được nhận mẫu voucher nào, mỗi tháng mấy mã. Không có dòng cho FREE - Member miễn phí
 * không nhận voucher.
 */
@Entity
@Table(name = "voucher_template_tier_rules", uniqueConstraints = {
        @UniqueConstraint(name = "uk_voucher_template_tier",
                columnNames = {"voucher_template_id", "tier"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherTemplateTierRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_template_id", nullable = false)
    private VoucherTemplate voucherTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 255)
    private MembershipTier tier;

    // >= 1, trần 10 theo validate của API Admin
    @Column(name = "quantity_per_month", nullable = false)
    private Integer quantityPerMonth;
}
