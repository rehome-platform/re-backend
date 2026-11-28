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

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherIssueServiceImpl implements VoucherIssueService {

    private static final String CODE_ALPHABET = "ACDEFGHJKMNPQRTUVWXY34679";
    private static final int CODE_BODY_LENGTH = 6;
    private static final int MAX_CODE_ATTEMPTS = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VoucherTemplateRepository voucherTemplateRepository;
    private final MemberVoucherRepository memberVoucherRepository;

    @Override
    @Transactional
    public int issueForSubscription(MemberSubscription subscription) {
        Instant now = Instant.now();
        VnTimeWindow.Window month = VnTimeWindow.thisMonth();

        Instant last = subscription.getLastVoucherIssuedAt();
        boolean alreadyIssuedThisMonth = last != null && !last.isBefore(month.from());

        int issued = 0;
        for (VoucherTemplate template : voucherTemplateRepository.findAllByActiveTrue()) {
            int quantity = quantityFor(template, subscription);
            if (quantity <= 0) {
                continue;
            }

            long already = memberVoucherRepository
                    .countBySourceSubscription_IdAndVoucherTemplate_IdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(
                            subscription.getId(), template.getId(), month.from(), month.to());
            long missing = quantity - already;
            if (missing <= 0) {
                continue;
            }
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
            subscription.setLastVoucherIssuedAt(now);
        }
        return issued;
    }

    private static int quantityFor(VoucherTemplate template, MemberSubscription subscription) {
        for (VoucherTemplateTierRule rule : template.getTierRules()) {
            if (rule.getTier() == subscription.getTier()) {
                return rule.getQuantityPerMonth() == null ? 0 : rule.getQuantityPerMonth();
            }
        }
        return 0;
    }

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
                .expiresAt(endOfDayAfter(now, template.getValidDays()))
                .build();
    }

    private static Instant endOfDayAfter(Instant from, int days) {
        LocalDate lastDay = from.atZone(VnTimeWindow.VN_ZONE).toLocalDate().plusDays(Math.max(days, 1));
        return lastDay.plusDays(1).atStartOfDay(VnTimeWindow.VN_ZONE).toInstant()
                .minus(1, ChronoUnit.SECONDS);
    }

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
