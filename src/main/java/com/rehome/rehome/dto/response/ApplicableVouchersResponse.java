package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.VoucherType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /vouchers/applicable - voucher dùng được cho đơn sắp đặt, kèm <b>số tiền giảm THẬT</b> của
 * từng mã.
 *
 * <p>⚠️ Mobile <b>phải</b> gọi API này để hiện số tiền giảm và <b>không được tự tính ở client</b>:
 * cùng một mã cho số tiền khác nhau tùy món đang ở kỳ ký gửi nào (trần = hoa hồng của kỳ hiện tại),
 * và với giỏ nhiều món còn có bước phân bổ theo tỷ lệ.
 *
 * <p>{@code subtotal} / {@code shippingFee} là của CẢ lần thanh toán, để FE dựng khối tổng tiền.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicableVouchersResponse {

    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    @Builder.Default
    private List<Item> vouchers = List.of();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private String id;
        private String code;
        private String name;
        private String description;
        private VoucherType type;
        private BigDecimal minOrderAmount;
        private Instant expiresAt;
        // false -> FE hiện xám ở nhóm "Chưa đủ điều kiện" kèm reason
        private boolean applicable;
        // Số tiền giảm thật cho cả lần thanh toán; 0 khi applicable = false
        private BigDecimal discountAmount;
        // null khi applicable = true
        private String reason;
    }
}
