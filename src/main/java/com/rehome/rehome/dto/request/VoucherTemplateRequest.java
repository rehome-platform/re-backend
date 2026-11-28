package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherTemplateRequest {

    private String name;
    private String description;
    private VoucherType type;
    private VoucherDiscountKind discountKind;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount;
    private Integer validDays;
    private Boolean active;
    private List<TierRule> tierRules;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierRule {
        private MembershipTier tier;
        private Integer quantityPerMonth;
    }
}
