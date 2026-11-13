package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST / PUT /api/admin/voucher-templates (màn W-04).
 *
 * <p>Validate (sai -> {@code VOUCHER_TEMPLATE_INVALID} 400):
 * <ul>
 *   <li>{@code type = ORDER_DISCOUNT} thì BẮT BUỘC {@code discountKind = PERCENT}. {@code FIXED}
 *       chỉ dùng được cho {@code SHIPPING}.</li>
 *   <li>{@code PERCENT} thì {@code discountValue} ∈ (0, 1] và {@code maxDiscountAmount} bắt buộc có.</li>
 *   <li>{@code FIXED} thì {@code discountValue > 0}.</li>
 *   <li>{@code validDays} ∈ [1, 365], {@code minOrderAmount} ≥ 0, {@code quantityPerMonth} ∈ [1, 10].</li>
 *   <li>{@code tierRules} không được chứa FREE và không trùng tier.</li>
 * </ul>
 *
 * <p>PUT thay thế toàn bộ {@code tierRules}: hạng không có trong danh sách là hạng không còn nhận
 * mẫu này nữa.
 */
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
