package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /vouchers/me - một mã trong ví voucher.
 *
 * <p>{@code usedOrderNos} có thể nhiều hơn một phần tử: một mã áp cho CẢ LẦN THANH TOÁN nên nếu
 * giỏ nhiều món (server tách thành nhiều đơn) thì mã trải trên tất cả các đơn đó. Tab "Đã dùng"
 * của M-04 hiện danh sách này, bấm vào từng mã đơn để mở chi tiết đơn.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberVoucherResponse {

    private String id;
    private String code;
    private String name;
    private String description;
    private VoucherType type;
    private VoucherDiscountKind discountKind;
    // FIXED: số tiền đồng. PERCENT: tỷ lệ, 0.10 = 10%
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount;
    private MemberVoucherStatus status;
    private Instant issuedAt;
    private Instant expiresAt;
    // Làm tròn lên; 0 nghĩa là hết hạn trong hôm nay
    private int daysRemaining;
    private Instant usedAt;
    @Builder.Default
    private List<String> usedOrderNos = List.of();
}
