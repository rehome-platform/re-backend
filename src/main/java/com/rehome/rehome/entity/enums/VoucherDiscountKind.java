package com.rehome.rehome.entity.enums;

/**
 * Cách tính mức giảm.
 *
 * <ul>
 *   <li>{@code FIXED} - {@code discount_value} là số tiền đồng. Chỉ dùng được cho type = SHIPPING.</li>
 *   <li>{@code PERCENT} - {@code discount_value} là tỷ lệ trong (0, 1], ví dụ 10% = 0.10, và
 *       {@code max_discount_amount} bắt buộc phải có.</li>
 * </ul>
 */
public enum VoucherDiscountKind {
    FIXED,
    PERCENT
}
