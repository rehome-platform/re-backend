package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// GET /api/admin/voucher-templates - mẫu voucher kèm rule theo hạng (màn W-04)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherTemplateResponse {

    private String id;
    private String name;
    private String description;
    private VoucherType type;
    private VoucherDiscountKind discountKind;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount;
    private Integer validDays;
    private Boolean active;
    // Chip hiển thị ở bảng W-04, ví dụ "SILVER ×2 · GOLD ×4 · DIAMOND ×6"
    @Builder.Default
    private List<TierRule> tierRules = List.of();
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TierRule {
        private MembershipTier tier;
        private Integer quantityPerMonth;
    }
}
