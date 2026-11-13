package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.VoucherType;
import com.rehome.rehome.entity.membership.MemberSubscription;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.entity.membership.VoucherTemplate;
import com.rehome.rehome.entity.membership.VoucherTemplateTierRule;
import com.rehome.rehome.repository.MemberVoucherRepository;
import com.rehome.rehome.repository.VoucherTemplateRepository;
import com.rehome.rehome.service.VoucherIssueService;
import com.rehome.rehome.util.VnTimeWindow;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phát voucher định kỳ cho một subscription theo {@code voucher_template_tier_rules} của hạng đó.
 *
 * <h2>Idempotent bằng hai lớp</h2>
 * <ol>
 *   <li>{@code last_voucher_issued_at} trong cùng tháng (giờ VN) -> bỏ qua cả lượt. Đây là chốt
 *       chính, chạy job hai lần trong ngày không phát đôi.</li>
 *   <li>Đếm số mã đã phát từ subscription này cho từng mẫu <i>trong tháng đó</i> -> chỉ phát phần
 *       còn thiếu. Lớp này cứu trường hợp lượt phát trước bị lỗi giữa đường (mẫu A đã phát, mẫu B
 *       chưa) mà {@code last_voucher_issued_at} vẫn kịp được ghi.</li>
 * </ol>
 *
 * <p>Cả hai lớp nằm trong cùng transaction với lúc insert, nên hai job chạy song song sẽ có một
 * bên thấy dữ liệu của bên kia (hoặc bị chặn ở commit) chứ không cùng phát.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherIssueServiceImpl implements VoucherIssueService {

    // Bỏ các ký tự dễ đọc sai khi người mua gõ tay hoặc đọc qua điện thoại:
    // O/0, I/1/L, Z/2, S/5, B/8 - giữ 20 chữ + 5 số
    private static final String CODE_ALPHABET = "ACDEFGHJKMNPQRTUVWXY34679";
    private static final int CODE_BODY_LENGTH = 6;
    // Mã UNIQUE ở DB; đụng trùng thì sinh lại. 25^6 ≈ 244 triệu tổ hợp nên vòng lặp này gần như
    // không bao giờ chạy quá 1 lần, nhưng vẫn phải có trần để không treo nếu alphabet bị thu nhỏ.
    private static final int MAX_CODE_ATTEMPTS = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VoucherTemplateRepository voucherTemplateRepository;
    private final MemberVoucherRepository memberVoucherRepository;

    @Override
    @Transactional
    public int issueForSubscription(MemberSubscription subscription) {
        Instant now = Instant.now();
        VnTimeWindow.Window month = VnTimeWindow.thisMonth();

        // Lớp 1: tháng này đã phát rồi thì thôi
        Instant last = subscription.getLastVoucherIssuedAt();
        boolean alreadyIssuedThisMonth = last != null && !last.isBefore(month.from());

        int issued = 0;
        for (VoucherTemplate template : voucherTemplateRepository.findAllByActiveTrue()) {
            int quantity = quantityFor(template, subscription);
            if (quantity <= 0) {
                continue;
            }

            // Lớp 2: chỉ phát phần còn thiếu của tháng
            long already = memberVoucherRepository
                    .countBySourceSubscription_IdAndVoucherTemplate_IdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(
                            subscription.getId(), template.getId(), month.from(), month.to());
            long missing = quantity - already;
            if (missing <= 0) {
                continue;
            }
            // Đã phát đủ ít nhất một mẫu và cờ tháng đã bật -> lượt này chỉ là chạy lại, không phát
            if (alreadyIssuedThisMonth && already > 0) {
                continue;
            }

            for (int i = 0; i < missing; i++) {
                memberVoucherRepository.save(buildVoucher(subscription, template, now));
                issued++;
            }
        }

        if (issued > 0) {
            subscription.setLastVoucherIssuedAt(now);
            log.info(">>>> Phat {} voucher cho subscriptionId={} (tier={})",
                    issued, subscription.getId(), subscription.getTier());
        } else if (last == null) {
            // Hạng không có rule nào (hoặc mọi mẫu đã tắt): vẫn ghi mốc để job không quét lại
            // subscription này mỗi ngày.
            subscription.setLastVoucherIssuedAt(now);
        }
        return issued;
    }

    /** Hạng của subscription được nhận mấy mã của mẫu này. 0 = không nhận. */
    private static int quantityFor(VoucherTemplate template, MemberSubscription subscription) {
        for (VoucherTemplateTierRule rule : template.getTierRules()) {
            if (rule.getTier() == subscription.getTier()) {
                return rule.getQuantityPerMonth() == null ? 0 : rule.getQuantityPerMonth();
            }
        }
        return 0;
    }

    /** Snapshot toàn bộ luật từ template: Admin sửa mẫu về sau không đổi mã đã phát. */
    private MemberVoucher buildVoucher(MemberSubscription subscription, VoucherTemplate template, Instant now) {
        return MemberVoucher.builder()
                .code(generateUniqueCode(template.getType()))
                .member(subscription.getMember())
                .voucherTemplate(template)
                .sourceSubscription(subscription)
                .type(template.getType())
                .discountKind(template.getDiscountKind())
                .discountValue(template.getDiscountValue())
                .maxDiscountAmount(template.getMaxDiscountAmount())
                .minOrderAmount(template.getMinOrderAmount())
                .status(MemberVoucherStatus.UNUSED)
                .issuedAt(now)
                // Hết hạn vào CUỐI ngày thứ validDays (giờ VN), không phải đúng giờ phút lúc phát -
                // mã "hiệu lực 30 ngày" phát 23:50 mà hết hạn 23:50 ngày thứ 30 thì người mua mất
                // gần một ngày và rất dễ hiểu là lỗi.
                .expiresAt(endOfDayAfter(now, template.getValidDays()))
                .build();
    }

    private static Instant endOfDayAfter(Instant from, int days) {
        LocalDate lastDay = from.atZone(VnTimeWindow.VN_ZONE).toLocalDate().plusDays(Math.max(days, 1));
        return lastDay.plusDays(1).atStartOfDay(VnTimeWindow.VN_ZONE).toInstant()
                .minus(1, ChronoUnit.SECONDS);
    }

    /** Tiền tố nói loại mã để người mua nhìn là biết, ví dụ {@code SHIP-7K2M9A} / {@code SALE-3B8Q1Z}. */
    private String generateUniqueCode(VoucherType type) {
        String prefix = type == VoucherType.SHIPPING ? "SHIP-" : "SALE-";
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            StringBuilder body = new StringBuilder(CODE_BODY_LENGTH);
            for (int i = 0; i < CODE_BODY_LENGTH; i++) {
                body.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = prefix + body;
            if (!memberVoucherRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException(
                "Không sinh được mã voucher duy nhất sau " + MAX_CODE_ATTEMPTS + " lần thử");
    }
}
