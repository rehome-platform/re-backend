package com.rehome.rehome.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.SystemConfigReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Bảng kiểm thử của đặc tả §12.2 và ví dụ phân bổ nhiều đơn của §12.6.
 *
 * <p>Hai ca quan trọng nhất là {@link SingleOrder#case5_orderDiscount_cappedByTerm1Commission} và
 * {@link SingleOrder#case6_sameVoucherSameItem_term3GivesMoreDiscount}: <b>cùng một mã voucher áp
 * lên cùng một món cho ra số tiền khác nhau tùy món đang ở kỳ ký gửi nào</b>. Đây là hệ quả trực
 * tiếp của luật "voucher giảm đơn không được vượt quá hoa hồng nền tảng thu được theo kỳ hiện tại"
 * - ai sửa {@code VoucherCalculator} mà làm hai test này đỏ thì đã phá luật nghiệp vụ, không phải
 * sửa test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherCalculatorTest {

    private static final BigDecimal TERM1_RATE = new BigDecimal("0.10");
    private static final BigDecimal TERM2_RATE = new BigDecimal("0.15");
    private static final BigDecimal TERM3_RATE = new BigDecimal("0.20");

    @Mock
    private SystemConfigReader systemConfigReader;

    private VoucherCalculator calculator;

    @BeforeEach
    void setUp() {
        // 1.00 = voucher giảm tối đa bằng đúng hoa hồng (nền tảng hòa vốn trên đơn đó)
        when(systemConfigReader.getDecimal(eq(SystemConfigKeys.VOUCHER_MAX_RATIO_OF_COMMISSION), any()))
                .thenReturn(BigDecimal.ONE);
        calculator = new VoucherCalculator(systemConfigReader);
    }

    // ================================================================ §12.2 - một đơn một món

    @Nested
    class SingleOrder {

        @Test
        @DisplayName("Ca 1: voucher ship FIXED 15.000, phí ship 30.000 -> giảm đúng 15.000")
        void case1_shippingFixed_normal() {
            MemberVoucher voucher = shippingVoucher("15000");
            BigDecimal discount = calculate(voucher, line("500000", "30000", TERM1_RATE));
            assertThat(discount).isEqualByComparingTo("15000");
        }

        @Test
        @DisplayName("Ca 2: voucher ship FIXED 15.000 nhưng phí ship thật chỉ 12.000 -> "
                + "chỉ giảm 12.000, KHÔNG hoàn phần thừa")
        void case2_shippingFixed_cappedByRealShippingFee() {
            MemberVoucher voucher = shippingVoucher("15000");
            BigDecimal discount = calculate(voucher, line("500000", "12000", TERM1_RATE));
            assertThat(discount).isEqualByComparingTo("12000");
        }

        @Test
        @DisplayName("Ca 3: giảm 5% trần 30.000 trên hàng 400.000 -> 20.000 (chưa chạm trần nào)")
        void case3_orderDiscount_plainPercent() {
            MemberVoucher voucher = orderDiscountVoucher("0.05", "30000", "0");
            BigDecimal discount = calculate(voucher, line("400000", "30000", TERM1_RATE));
            assertThat(discount).isEqualByComparingTo("20000");
        }

        @Test
        @DisplayName("Ca 4: giảm 5% trên hàng 1.000.000 = 50.000 -> bị chặn bởi max_discount_amount 30.000")
        void case4_orderDiscount_cappedByMaxDiscountAmount() {
            MemberVoucher voucher = orderDiscountVoucher("0.05", "30000", "0");
            BigDecimal discount = calculate(voucher, line("1000000", "30000", TERM1_RATE));
            assertThat(discount).isEqualByComparingTo("30000");
        }

        @Test
        @DisplayName("Ca 5: giảm 20% trần 100.000 trên hàng 200.000 kỳ 1 -> bị chặn bởi hoa hồng "
                + "kỳ 1 (10% = 20.000), không phải bởi trần mã")
        void case5_orderDiscount_cappedByTerm1Commission() {
            MemberVoucher voucher = orderDiscountVoucher("0.20", "100000", "0");
            BigDecimal discount = calculate(voucher, line("200000", "30000", TERM1_RATE));
            // 20% × 200.000 = 40.000 nhưng hoa hồng kỳ 1 chỉ 20.000
            assertThat(discount).isEqualByComparingTo("20000");
        }

        @Test
        @DisplayName("Ca 6: CÙNG mã, CÙNG món, nhưng kỳ 3 (hoa hồng 20% = 40.000) -> giảm được 40.000. "
                + "Cùng mã cho số tiền khác nhau tùy kỳ ký gửi - đây là chủ ý")
        void case6_sameVoucherSameItem_term3GivesMoreDiscount() {
            MemberVoucher voucher = orderDiscountVoucher("0.20", "100000", "0");
            BigDecimal discount = calculate(voucher, line("200000", "30000", TERM3_RATE));
            assertThat(discount).isEqualByComparingTo("40000");
        }

        @Test
        @DisplayName("Ca 7: đơn 150.000 chưa đạt min_order_amount 200.000 -> VOUCHER_MIN_ORDER_NOT_MET 400")
        void case7_minOrderNotMet() {
            MemberVoucher voucher = orderDiscountVoucher("0.05", "30000", "200000");
            assertThatThrownBy(() -> calculator.assertUsable(
                    voucher, voucher.getMember().getId(), new BigDecimal("150000"), Instant.now()))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET);
        }

        @Test
        @DisplayName("Kỳ 2 (15%) nằm giữa kỳ 1 và kỳ 3 - xác nhận trần đi theo đúng tỷ lệ của kỳ")
        void term2_sitsBetweenTerm1AndTerm3() {
            MemberVoucher voucher = orderDiscountVoucher("0.20", "100000", "0");
            BigDecimal discount = calculate(voucher, line("200000", "30000", TERM2_RATE));
            assertThat(discount).isEqualByComparingTo("30000");
        }

        @Test
        @DisplayName("Voucher ship gặp đơn miễn phí ship (0đ) -> tổng giảm 0 -> VOUCHER_NOT_APPLICABLE 400")
        void shippingVoucher_onFreeShippingOrder_isNotApplicable() {
            MemberVoucher voucher = shippingVoucher("15000");
            assertThatThrownBy(() -> calculator.calculate(
                    voucher, List.of(line("500000", "0", TERM1_RATE)), true))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_NOT_APPLICABLE);
        }

        @Test
        @DisplayName("Luồng xem trước (throwWhenZero = false) trả tổng 0 thay vì ném lỗi, "
                + "để GET /vouchers/applicable hiện được lý do")
        void previewMode_returnsZeroInsteadOfThrowing() {
            MemberVoucher voucher = shippingVoucher("15000");
            VoucherCalculator.Result result =
                    calculator.calculate(voucher, List.of(line("500000", "0", TERM1_RATE)), false);
            assertThat(result.totalDiscount()).isEqualByComparingTo("0");
        }

        /**
         * Trần {@code voucher_max_ratio_of_commission} là tham số Admin sửa được: hạ xuống dưới
         * 1.00 để nền tảng giữ lại một phần hoa hồng thay vì hòa vốn trên đơn.
         */
        @Test
        @DisplayName("voucher_max_ratio_of_commission = 0.50 -> chỉ cho giảm tối đa nửa hoa hồng")
        void commissionRatioConfig_tightensTheCap() {
            when(systemConfigReader.getDecimal(
                    eq(SystemConfigKeys.VOUCHER_MAX_RATIO_OF_COMMISSION), any()))
                    .thenReturn(new BigDecimal("0.50"));

            MemberVoucher voucher = orderDiscountVoucher("0.20", "100000", "0");
            BigDecimal discount = calculate(voucher, line("200000", "30000", TERM1_RATE));
            // Hoa hồng kỳ 1 = 20.000, trần 50% -> 10.000
            assertThat(discount).isEqualByComparingTo("10000");
        }
    }

    // ================================================================ §12.6 - nhiều đơn một mã

    @Nested
    class MultipleOrders {

        @Test
        @DisplayName("Ví dụ §12.6: mã 10% trần 50.000, giỏ 2 món mỗi món 400.000 -> "
                + "d thô 40.000 + 40.000 = 80.000, áp trần tổng còn 25.000 mỗi đơn")
        void capAppliesOnTotal_thenSplitsProportionally() {
            MemberVoucher voucher = orderDiscountVoucher("0.10", "50000", "0");

            VoucherCalculator.Result result = calculator.calculate(voucher, List.of(
                    new VoucherCalculator.OrderLine("A", new BigDecimal("400000"),
                            new BigDecimal("20000"), new BigDecimal("400000"), TERM1_RATE),
                    new VoucherCalculator.OrderLine("B", new BigDecimal("400000"),
                            new BigDecimal("20000"), new BigDecimal("400000"), TERM1_RATE)), true);

            assertThat(result.totalDiscount()).isEqualByComparingTo("50000");
            assertThat(result.discountOf("A")).isEqualByComparingTo("25000");
            assertThat(result.discountOf("B")).isEqualByComparingTo("25000");
        }

        @Test
        @DisplayName("Trần max_discount_amount áp trên TỔNG, không áp từng đơn - nếu áp từng đơn thì "
                + "mã 'giảm tối đa 50.000' sẽ thành 50.000 × số đơn")
        void cap_isNotAppliedPerOrder() {
            MemberVoucher voucher = orderDiscountVoucher("0.10", "50000", "0");

            VoucherCalculator.Result result = calculator.calculate(voucher, List.of(
                    line("A", "1000000", "20000", TERM3_RATE),
                    line("B", "1000000", "20000", TERM3_RATE)), true);

            assertThat(result.totalDiscount()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("SUM(d_i) luôn khớp đúng tổng, không lệch một xu: phần chênh do làm tròn được "
                + "dồn vào đơn có subtotal lớn nhất")
        void roundingDrift_goesToBiggestOrder() {
            // 3 đơn, trần tổng 10.000 -> chia 3 không hết, phải có đơn nhận phần lẻ
            MemberVoucher voucher = orderDiscountVoucher("0.20", "10000", "0");

            VoucherCalculator.Result result = calculator.calculate(voucher, List.of(
                    line("small", "100000", "10000", TERM3_RATE),
                    line("medium", "200000", "10000", TERM3_RATE),
                    line("big", "300000", "10000", TERM3_RATE)), true);

            BigDecimal sum = result.allocations().stream()
                    .map(VoucherCalculator.Allocation::discount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo(result.totalDiscount());
            assertThat(result.totalDiscount()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("min_order_amount so với TỔNG subtotal của cả lần thanh toán, không so từng đơn: "
                + "hai món 150.000 đạt được mã yêu cầu đơn từ 200.000")
        void minOrderAmount_comparesAgainstTotalSubtotal() {
            MemberVoucher voucher = orderDiscountVoucher("0.05", "30000", "200000");
            UUID ownerId = voucher.getMember().getId();

            // Tổng 300.000 >= 200.000 -> không ném lỗi
            calculator.assertUsable(voucher, ownerId, new BigDecimal("300000"), Instant.now());
        }

        @Test
        @DisplayName("Trần hoa hồng áp RIÊNG từng đơn: đơn kỳ 1 bị chặn chặt hơn đơn kỳ 3 "
                + "dù cùng giá và cùng mã")
        void commissionCap_isPerOrder() {
            // Trần tổng để rất cao để không can thiệp, nhìn rõ trần từng đơn
            MemberVoucher voucher = orderDiscountVoucher("0.20", "999000", "0");

            VoucherCalculator.Result result = calculator.calculate(voucher, List.of(
                    line("term1", "200000", "20000", TERM1_RATE),
                    line("term3", "200000", "20000", TERM3_RATE)), true);

            assertThat(result.discountOf("term1")).isEqualByComparingTo("20000");
            assertThat(result.discountOf("term3")).isEqualByComparingTo("40000");
            assertThat(result.totalDiscount()).isEqualByComparingTo("60000");
        }
    }

    // ================================================================ B1 - điều kiện dùng mã

    @Nested
    class Eligibility {

        @Test
        @DisplayName("Mã của người khác -> VOUCHER_FORBIDDEN 403")
        void otherMembersVoucher_isForbidden() {
            MemberVoucher voucher = shippingVoucher("15000");
            assertThatThrownBy(() -> calculator.assertUsable(
                    voucher, UUID.randomUUID(), new BigDecimal("500000"), Instant.now()))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_FORBIDDEN);
        }

        @Test
        @DisplayName("Mã đã dùng -> VOUCHER_ALREADY_USED 409")
        void usedVoucher_isRejected() {
            MemberVoucher voucher = shippingVoucher("15000");
            voucher.setStatus(MemberVoucherStatus.USED);
            assertThatThrownBy(() -> calculator.assertUsable(
                    voucher, voucher.getMember().getId(), new BigDecimal("500000"), Instant.now()))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_ALREADY_USED);
        }

        @Test
        @DisplayName("Mã hết hạn -> VOUCHER_EXPIRED 409")
        void expiredVoucher_isRejected() {
            MemberVoucher voucher = shippingVoucher("15000");
            voucher.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
            assertThatThrownBy(() -> calculator.assertUsable(
                    voucher, voucher.getMember().getId(), new BigDecimal("500000"), Instant.now()))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VOUCHER_EXPIRED);
        }

        @Test
        @DisplayName("describeIneligibility trả lý do thay vì ném lỗi, cho luồng xem trước")
        void describeIneligibility_returnsReasonForPreview() {
            MemberVoucher voucher = orderDiscountVoucher("0.05", "30000", "200000");
            String reason = calculator.describeIneligibility(
                    voucher, new BigDecimal("150000"), Instant.now());
            assertThat(reason).contains("200.000đ");
        }

        @Test
        @DisplayName("Mã đủ điều kiện -> describeIneligibility trả null")
        void describeIneligibility_returnsNullWhenUsable() {
            MemberVoucher voucher = shippingVoucher("15000");
            assertThat(calculator.describeIneligibility(
                    voucher, new BigDecimal("500000"), Instant.now())).isNull();
        }
    }

    // ================================================================ Helper

    private BigDecimal calculate(MemberVoucher voucher, VoucherCalculator.OrderLine line) {
        return calculator.calculate(voucher, List.of(line), true).totalDiscount();
    }

    private static VoucherCalculator.OrderLine line(String subtotal, String shippingFee,
                                                     BigDecimal commissionRate) {
        return line("only", subtotal, shippingFee, commissionRate);
    }

    private static VoucherCalculator.OrderLine line(String key, String subtotal, String shippingFee,
                                                     BigDecimal commissionRate) {
        return new VoucherCalculator.OrderLine(key, new BigDecimal(subtotal),
                new BigDecimal(shippingFee), new BigDecimal(subtotal), commissionRate);
    }

    private static MemberVoucher shippingVoucher(String amount) {
        return baseVoucher()
                .type(VoucherType.SHIPPING)
                .discountKind(VoucherDiscountKind.FIXED)
                .discountValue(new BigDecimal(amount))
                .maxDiscountAmount(null)
                .minOrderAmount(BigDecimal.ZERO)
                .build();
    }

    private static MemberVoucher orderDiscountVoucher(String percent, String maxDiscount, String minOrder) {
        return baseVoucher()
                .type(VoucherType.ORDER_DISCOUNT)
                // ORDER_DISCOUNT luôn là PERCENT (chặn ở VoucherTemplateAdminServiceImpl)
                .discountKind(VoucherDiscountKind.PERCENT)
                .discountValue(new BigDecimal(percent))
                .maxDiscountAmount(new BigDecimal(maxDiscount))
                .minOrderAmount(new BigDecimal(minOrder))
                .build();
    }

    private static MemberVoucher.MemberVoucherBuilder baseVoucher() {
        Account owner = Account.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        return MemberVoucher.builder()
                .id(UUID.randomUUID())
                .code("TEST-ABC123")
                .member(owner)
                .status(MemberVoucherStatus.UNUSED)
                .issuedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .expiresAt(Instant.now().plus(29, ChronoUnit.DAYS));
    }
}
