package com.rehome.rehome.entity.enums;

/**
 * Voucher giảm vào đâu.
 *
 * <ul>
 *   <li>{@code SHIPPING} - giảm phí vận chuyển, không giảm quá phí ship thật của đơn.</li>
 *   <li>{@code ORDER_DISCOUNT} - giảm tiền hàng. BẮT BUỘC là PERCENT và không được giảm quá hoa hồng
 *       nền tảng thu được theo kỳ ký gửi hiện tại của món (xem VoucherCalculator).</li>
 * </ul>
 */
public enum VoucherType {
    SHIPPING,
    ORDER_DISCOUNT
}
