package com.rehome.rehome.component;

import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.SystemConfigReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tính số tiền giảm của một mã voucher cho MỘT LẦN THANH TOÁN gồm N đơn, rồi phân bổ ngược về từng
 * đơn. Đặc tả §12.1 (một đơn) và §12.6 (nhiều đơn).
 *
 * <p><b>Vì sao áp cho cả lần thanh toán chứ không cho một đơn lẻ?</b> Giỏ 3 món tạo 3 đơn riêng
 * nhưng người mua chỉ thấy một màn thanh toán và trả một lần tiền (MoMo/VNPay cũng chỉ mở một giao
 * dịch cho cả lô - xem {@code CLAUDE.md} §Order payment). Giỏ một món - tình huống phổ biến nhất
 * của hàng second-hand vì mỗi món là duy nhất - cho kết quả giống hệt cách áp theo đơn, phần phân
 * bổ chỉ chạy khi giỏ nhiều món.
 *
 * <p><b>Hai cái trần khác nhau, đừng lẫn:</b>
 * <ul>
 *   <li>Trần <i>từng đơn</i>: phí ship thật (SHIPPING), hoặc hoa hồng nền tảng thu được theo kỳ ký
 *       gửi hiện tại của món (ORDER_DISCOUNT). Áp ở bước B3.</li>
 *   <li>Trần <i>tổng</i>: {@code max_discount_amount} của mã. Áp ở bước C3 trên TỔNG, rồi chia lại
 *       theo tỷ lệ - không áp cho từng đơn, nếu không mã "giảm tối đa 50k" sẽ thành 50k × số đơn.</li>
 * </ul>
 *
 * <p><b>Tuyệt đối không đụng tới {@code price_at_order}</b>: người ký gửi vẫn nhận tiền tính trên
 * giá bán gốc, phần giảm do nền tảng chịu (đặc tả §1.3, §13.4).
 */
@Component
@RequiredArgsConstructor
public class VoucherCalculator {

    private static final int MONEY_SCALE = 2;

    private final SystemConfigReader systemConfigReader;

    /**
     * Một đơn sắp tạo (hoặc đang xem trước ở {@code GET /vouchers/applicable}).
     *
     * @param orderKey       khóa để nơi gọi nhận lại phần giảm của mình; với đơn đã tồn tại thì là
     *                       orderId, với đơn chưa tạo thì là productId
     * @param subtotal       tiền hàng của đơn
     * @param shippingFee    phí ship của đơn
     * @param priceAtOrder   giá chốt của món - mốc tính hoa hồng, KHÔNG phải subtotal sau giảm
     * @param commissionRate tỷ lệ hoa hồng của kỳ ký gửi hiện tại (kỳ 1 = 0.10, kỳ 2 = 0.15, kỳ 3 = 0.20)
     */
    public record OrderLine(
            String orderKey,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal priceAtOrder,
            BigDecimal commissionRate) {}

    /** Phần giảm đã phân bổ cho một đơn. */
    public record Allocation(String orderKey, BigDecimal discount) {}

    /** Kết quả tính cho cả lần thanh toán. */
    public record Result(BigDecimal totalDiscount, List<Allocation> allocations) {

        public BigDecimal discountOf(String orderKey) {
            return allocations.stream()
                    .filter(a -> a.orderKey().equals(orderKey))
                    .map(Allocation::discount)
                    .findFirst()
                    .orElse(BigDecimal.ZERO.setScale(MONEY_SCALE));
        }
    }

    // ================================================================ B1 - kiểm tra điều kiện

    /**
     * Bước B1 / C1: quyền sở hữu, trạng thái, hạn dùng, và giá trị đơn tối thiểu so với TỔNG
     * subtotal của cả lần thanh toán (không phải từng đơn).
     *
     * <p>Ném đúng mã lỗi của đặc tả §12.1 để FE branch được theo {@code code}.
     */
    public void assertUsable(MemberVoucher voucher, UUID callerId, BigDecimal totalSubtotal, Instant now) {
        if (!voucher.getMember().getId().equals(callerId)) {
            throw new AppException(ErrorCode.VOUCHER_FORBIDDEN);                                     // 403
        }
        if (voucher.getStatus() != MemberVoucherStatus.UNUSED) {
            throw new AppException(ErrorCode.VOUCHER_ALREADY_USED);                                  // 409
        }
        if (!voucher.getExpiresAt().isAfter(now)) {
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);                                       // 409
        }
        if (totalSubtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new AppException(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET);                             // 400
        }
    }

    /** Bản không ném lỗi, cho {@code GET /vouchers/applicable} vốn phải trả cả mã chưa đủ điều kiện. */
    public String describeIneligibility(MemberVoucher voucher, BigDecimal totalSubtotal, Instant now) {
        if (voucher.getStatus() != MemberVoucherStatus.UNUSED) {
            return "Mã đã được sử dụng";
        }
        if (!voucher.getExpiresAt().isAfter(now)) {
            return "Mã đã hết hạn";
        }
        if (totalSubtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
            return "Đơn hàng chưa đạt " + formatDong(voucher.getMinOrderAmount());
        }
        return null;
    }

    // ================================================================ B2–B4 + C2–C4

    /**
     * Tính và phân bổ. Không kiểm tra điều kiện B1 - nơi gọi phải gọi {@link #assertUsable} trước
     * (luồng đặt hàng) hoặc {@link #describeIneligibility} (luồng xem trước).
     *
     * @param throwWhenZero true ở luồng đặt hàng: tổng giảm bằng 0 nghĩa là mã vô nghĩa với đơn này
     *                      -> ném {@code VOUCHER_NOT_APPLICABLE} 400 và không tạo đơn nào mang mã.
     *                      false ở luồng xem trước: trả tổng 0 để FE hiện lý do.
     */
    public Result calculate(MemberVoucher voucher, List<OrderLine> lines, boolean throwWhenZero) {
        if (lines == null || lines.isEmpty()) {
            if (throwWhenZero) {
                throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE);                            // 400
            }
            return new Result(zero(), List.of());
        }

        BigDecimal commissionCapRatio = systemConfigReader.getDecimal(
                SystemConfigKeys.VOUCHER_MAX_RATIO_OF_COMMISSION,
                SystemConfigKeys.DEFAULT_VOUCHER_MAX_RATIO_OF_COMMISSION);

        // C2: mức giảm thô của từng đơn, đã áp trần riêng của đơn đó (B2 + B3)
        List<BigDecimal> raw = new ArrayList<>(lines.size());
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine line : lines) {
            BigDecimal d = rawDiscountForLine(voucher, line, commissionCapRatio);
            raw.add(d);
            total = total.add(d);
        }

        // C3: áp max_discount_amount trên TỔNG rồi chia lại theo tỷ lệ
        BigDecimal cap = voucher.getMaxDiscountAmount();
        if (cap != null && total.compareTo(cap) > 0 && total.signum() > 0) {
            BigDecimal k = cap.divide(total, 10, RoundingMode.HALF_UP);
            for (int i = 0; i < raw.size(); i++) {
                raw.set(i, raw.get(i).multiply(k));
            }
            total = cap;
        }

        // C4: làm tròn từng đơn rồi dồn phần chênh vào đơn có subtotal LỚN NHẤT, để
        // SUM(d_i) khớp đúng total, không lệch một xu
        List<BigDecimal> rounded = new ArrayList<>(raw.size());
        BigDecimal sumRounded = BigDecimal.ZERO;
        for (BigDecimal d : raw) {
            BigDecimal r = d.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            rounded.add(r);
            sumRounded = sumRounded.add(r);
        }
        total = total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal drift = total.subtract(sumRounded);
        if (drift.signum() != 0) {
            int biggest = indexOfBiggestSubtotal(lines);
            rounded.set(biggest, rounded.get(biggest).add(drift));
        }

        // B4 / C5: tổng <= 0 nghĩa là mã không giảm được gì cho lần thanh toán này
        if (total.signum() <= 0) {
            if (throwWhenZero) {
                throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE);                            // 400
            }
            return new Result(zero(), buildZeroAllocations(lines));
        }

        List<Allocation> allocations = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            allocations.add(new Allocation(lines.get(i).orderKey(), rounded.get(i)));
        }
        return new Result(total, allocations);
    }

    /** B2 + B3 cho một đơn: mức giảm thô, đã áp trần riêng của đơn, CHƯA áp max_discount_amount. */
    private BigDecimal rawDiscountForLine(MemberVoucher voucher, OrderLine line, BigDecimal commissionCapRatio) {
        BigDecimal base = voucher.getType() == VoucherType.SHIPPING
                ? line.shippingFee()
                : line.subtotal();

        // FIXED chỉ dùng được cho SHIPPING (validate ở API Admin §9.3), nên nhánh này không
        // bao giờ cho ORDER_DISCOUNT một số tiền cố định
        BigDecimal raw = voucher.getDiscountKind() == VoucherDiscountKind.FIXED
                ? voucher.getDiscountValue()
                : base.multiply(voucher.getDiscountValue());

        if (voucher.getType() == VoucherType.SHIPPING) {
            // Không giảm quá phí ship THẬT; phí ship thấp hơn mệnh giá thì không hoàn phần thừa
            return min(raw, line.shippingFee());
        }

        // ORDER_DISCOUNT: commissionRate CHÍNH LÀ tỷ lệ của kỳ ký gửi hiện tại, đã chốt vào
        // order_items lúc tạo đơn. Cùng một mã áp lên cùng một món cho số tiền KHÁC NHAU tùy món
        // đang ở kỳ nào (kỳ 1 = 10% -> giảm được ít, kỳ 3 = 20% -> giảm được nhiều hơn).
        // Đây là chủ ý, không phải lỗi - xem ca 5 và 6 ở bảng §12.2.
        BigDecimal commission = line.priceAtOrder().multiply(line.commissionRate());
        BigDecimal commissionCap = commission.multiply(commissionCapRatio);
        return min(min(raw, commissionCap), line.subtotal());
    }

    private static int indexOfBiggestSubtotal(List<OrderLine> lines) {
        int biggest = 0;
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).subtotal().compareTo(lines.get(biggest).subtotal()) > 0) {
                biggest = i;
            }
        }
        return biggest;
    }

    private static List<Allocation> buildZeroAllocations(List<OrderLine> lines) {
        List<Allocation> zeros = new ArrayList<>(lines.size());
        for (OrderLine line : lines) {
            zeros.add(new Allocation(line.orderKey(), zero()));
        }
        return zeros;
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    private static String formatDong(BigDecimal amount) {
        return String.format("%,d", amount.setScale(0, RoundingMode.HALF_UP).longValueExact())
                .replace(',', '.') + "đ";
    }
}
