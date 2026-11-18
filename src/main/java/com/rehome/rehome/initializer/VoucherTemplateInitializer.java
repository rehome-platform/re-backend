package com.rehome.rehome.initializer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import com.rehome.rehome.entity.membership.VoucherTemplate;
import com.rehome.rehome.entity.membership.VoucherTemplateTierRule;
import com.rehome.rehome.repository.VoucherTemplateRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed 5 mẫu voucher mẫu, khớp bảng gợi ý đã chốt với Admin. Chỉ tạo khi chưa có mẫu cùng tên, nên
 * Admin sửa/tắt/xoá rồi thì restart không dựng lại.
 *
 * <h2>5 mẫu, cố ý khác nhau để minh hoạ đủ cách dùng</h2>
 * <ul>
 *   <li><b>Freeship 15.000đ</b> - FIXED, không yêu cầu đơn tối thiểu, phát đều cả 3 hạng.</li>
 *   <li><b>Freeship 30.000đ cho đơn từ 300k</b> - FIXED cao hơn nhưng có đơn tối thiểu, CHỈ phát
 *       Vàng/Kim cương để tạo khác biệt giữa các hạng.</li>
 *   <li><b>Giảm 10% đơn hàng, tối đa 50.000đ</b> - PERCENT khớp đúng hoa hồng kỳ 1 (10%, kỳ phổ
 *       biến nhất), nên % quảng cáo gần như luôn đạt được thật, không bị cắt ngầm.</li>
 *   <li><b>Giảm 5% đơn hàng, tối đa 30.000đ</b> - PERCENT thấp, phát nhiều, dùng cho đơn nhỏ hơn.</li>
 *   <li><b>Giảm 15% đơn từ 500k, tối đa 80.000đ</b> - PERCENT cao hơn hoa hồng kỳ 1, CHỈ phát Kim
 *       cương; thực tế mức giảm thường bị trần hoa hồng của món cắt xuống còn ~10-20% tuỳ kỳ,
 *       không đạt đủ 15% với món đang ở kỳ 1 - ghi rõ trong mô tả để không gây hiểu lầm.</li>
 * </ul>
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.initializer.seed-enabled", havingValue = "true")
public class VoucherTemplateInitializer implements CommandLineRunner {

    private final VoucherTemplateRepository voucherTemplateRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        created += seed("Freeship 15.000đ",
                "Giảm tối đa 15.000đ phí vận chuyển, không hoàn phần dư nếu phí ship thật thấp hơn.",
                VoucherType.SHIPPING, VoucherDiscountKind.FIXED, "15000", null, "0", 30,
                2, 4, 6);

        created += seed("Freeship 30.000đ cho đơn từ 300k",
                "Giảm tối đa 30.000đ phí vận chuyển, áp dụng đơn từ 300.000đ.",
                VoucherType.SHIPPING, VoucherDiscountKind.FIXED, "30000", null, "300000", 30,
                null, 2, 4);

        created += seed("Giảm 10% đơn hàng, tối đa 50.000đ",
                "Giảm 10% tiền hàng, tối đa 50.000đ. Phần giảm do ReHome chịu.",
                VoucherType.ORDER_DISCOUNT, VoucherDiscountKind.PERCENT, "0.10", "50000", "200000", 30,
                1, 2, 3);

        created += seed("Giảm 5% đơn hàng, tối đa 30.000đ",
                "Giảm 5% tiền hàng, tối đa 30.000đ. Dùng được cho đơn từ 100.000đ.",
                VoucherType.ORDER_DISCOUNT, VoucherDiscountKind.PERCENT, "0.05", "30000", "100000", 45,
                2, 4, 6);

        created += seed("Giảm 15% đơn từ 500k, tối đa 80.000đ",
                "Ưu đãi riêng hạng Kim cương: giảm 15% đơn từ 500.000đ, tối đa 80.000đ. Thực tế có "
                        + "thể bị giới hạn thêm theo hoa hồng món (kỳ 1 chỉ đạt ~10%).",
                VoucherType.ORDER_DISCOUNT, VoucherDiscountKind.PERCENT, "0.15", "80000", "500000", 30,
                null, null, 2);

        log.info("Seed voucher_templates: tạo {} mẫu mới", created);
    }

    private int seed(String name, String description, VoucherType type, VoucherDiscountKind discountKind,
                      String discountValue, String maxDiscountAmount, String minOrderAmount, int validDays,
                      Integer silver, Integer gold, Integer diamond) {
        if (voucherTemplateRepository.existsByName(name)) {
            return 0;
        }

        VoucherTemplate template = VoucherTemplate.builder()
                .name(name)
                .description(description)
                .type(type)
                .discountKind(discountKind)
                .discountValue(new BigDecimal(discountValue))
                .maxDiscountAmount(maxDiscountAmount == null ? null : new BigDecimal(maxDiscountAmount))
                .minOrderAmount(new BigDecimal(minOrderAmount))
                .validDays(validDays)
                .active(true)
                .build();
        addRule(template, MembershipTier.SILVER, silver);
        addRule(template, MembershipTier.GOLD, gold);
        addRule(template, MembershipTier.DIAMOND, diamond);
        voucherTemplateRepository.save(template);
        return 1;
    }

    // quantity null = hạng đó không nhận mẫu này (ví dụ chỉ phát riêng Kim cương)
    private static void addRule(VoucherTemplate template, MembershipTier tier, Integer quantity) {
        if (quantity == null) {
            return;
        }
        template.getTierRules().add(VoucherTemplateTierRule.builder()
                .voucherTemplate(template)
                .tier(tier)
                .quantityPerMonth(quantity)
                .build());
    }
}
