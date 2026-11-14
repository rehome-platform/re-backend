package com.rehome.rehome.initializer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import com.rehome.rehome.entity.membership.VoucherTemplate;
import com.rehome.rehome.entity.membership.VoucherTemplateTierRule;
import com.rehome.rehome.repository.VoucherTemplateRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed 2 mẫu voucher khớp bảng quyền lợi (đặc tả §11 W-04). Chỉ tạo khi chưa có mẫu cùng tên, nên
 * Admin sửa/tắt rồi thì restart không dựng lại.
 *
 * <h2>Hai mẫu cố ý khác bản chất</h2>
 * <ul>
 *   <li><b>Miễn phí vận chuyển</b> - FIXED 15.000đ. Phí ship thật thấp hơn thì chỉ giảm bằng đúng
 *       phí ship, <b>không hoàn phần thừa</b>.</li>
 *   <li><b>Giảm giá đơn hàng</b> - PERCENT 10%, trần 50.000đ. Nhưng trần THẬT thường là hoa hồng
 *       của kỳ ký gửi chứ không phải {@code max_discount_amount}: món kỳ 1 chỉ cho hoa hồng 10%,
 *       nên 10% của giá hàng gần như luôn chạm trần hoa hồng trước. <b>Đây là chủ ý, không phải lỗi</b> -
 *       nền tảng không được lỗ trên đơn.</li>
 * </ul>
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.initializer.seed-enabled", havingValue = "true")
public class VoucherTemplateInitializer implements CommandLineRunner {

    private static final String SHIPPING_NAME = "Miễn phí vận chuyển";
    private static final String ORDER_DISCOUNT_NAME = "Giảm giá đơn hàng";

    private final VoucherTemplateRepository voucherTemplateRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;

        if (!voucherTemplateRepository.existsByName(SHIPPING_NAME)) {
            VoucherTemplate shipping = VoucherTemplate.builder()
                    .name(SHIPPING_NAME)
                    .description("Giảm tối đa 15.000đ phí vận chuyển")
                    .type(VoucherType.SHIPPING)
                    .discountKind(VoucherDiscountKind.FIXED)
                    .discountValue(new BigDecimal("15000"))
                    // FIXED không có max riêng: trần là chính phí ship thật của đơn
                    .maxDiscountAmount(null)
                    .minOrderAmount(BigDecimal.ZERO)
                    .validDays(30)
                    .active(true)
                    .build();
            addRules(shipping, 2, 4, 6);
            voucherTemplateRepository.save(shipping);
            created++;
        }

        if (!voucherTemplateRepository.existsByName(ORDER_DISCOUNT_NAME)) {
            VoucherTemplate orderDiscount = VoucherTemplate.builder()
                    .name(ORDER_DISCOUNT_NAME)
                    .description("Giảm 10% tiền hàng, tối đa 50.000đ")
                    .type(VoucherType.ORDER_DISCOUNT)
                    // ORDER_DISCOUNT bắt buộc PERCENT - xem VoucherTemplateAdminServiceImpl
                    .discountKind(VoucherDiscountKind.PERCENT)
                    .discountValue(new BigDecimal("0.10"))
                    .maxDiscountAmount(new BigDecimal("50000"))
                    .minOrderAmount(BigDecimal.ZERO)
                    .validDays(30)
                    .active(true)
                    .build();
            addRules(orderDiscount, 1, 2, 4);
            voucherTemplateRepository.save(orderDiscount);
            created++;
        }

        log.info("Seed voucher_templates: tạo {} mẫu mới", created);
    }

    /** Số mã mỗi tháng cho SILVER / GOLD / DIAMOND. Không có dòng FREE: hạng miễn phí không nhận mã. */
    private static void addRules(VoucherTemplate template, int silver, int gold, int diamond) {
        List<VoucherTemplateTierRule> rules = List.of(
                rule(template, MembershipTier.SILVER, silver),
                rule(template, MembershipTier.GOLD, gold),
                rule(template, MembershipTier.DIAMOND, diamond));
        template.getTierRules().addAll(rules);
    }

    private static VoucherTemplateTierRule rule(VoucherTemplate template, MembershipTier tier, int quantity) {
        return VoucherTemplateTierRule.builder()
                .voucherTemplate(template)
                .tier(tier)
                .quantityPerMonth(quantity)
                .build();
    }
}
