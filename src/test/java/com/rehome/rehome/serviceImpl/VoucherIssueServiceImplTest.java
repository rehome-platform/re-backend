package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import com.rehome.rehome.entity.membership.MemberSubscription;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.entity.membership.VoucherTemplate;
import com.rehome.rehome.entity.membership.VoucherTemplateTierRule;
import com.rehome.rehome.repository.MemberVoucherRepository;
import com.rehome.rehome.repository.VoucherTemplateRepository;
import com.rehome.rehome.util.VnTimeWindow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Phát voucher định kỳ. Yêu cầu quan trọng nhất của đặc tả §14: <b>job phải idempotent</b> - chạy
 * lại hai lần trong cùng một tháng không được phát đôi.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherIssueServiceImplTest {

    @Mock private VoucherTemplateRepository voucherTemplateRepository;
    @Mock private MemberVoucherRepository memberVoucherRepository;

    @InjectMocks
    private VoucherIssueServiceImpl service;

    private Account member;
    private VoucherTemplate shippingTemplate;
    private VoucherTemplate orderDiscountTemplate;
    private final List<MemberVoucher> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        member = Account.builder().id(UUID.randomUUID()).email("m@example.com").build();

        shippingTemplate = template("Miễn phí vận chuyển", VoucherType.SHIPPING,
                VoucherDiscountKind.FIXED, "15000", null, 2, 4, 6);
        orderDiscountTemplate = template("Giảm giá đơn hàng", VoucherType.ORDER_DISCOUNT,
                VoucherDiscountKind.PERCENT, "0.10", "50000", 1, 2, 4);

        when(voucherTemplateRepository.findAllByActiveTrue())
                .thenReturn(List.of(shippingTemplate, orderDiscountTemplate));
        when(memberVoucherRepository.existsByCode(anyString())).thenReturn(false);
        when(memberVoucherRepository.save(any(MemberVoucher.class))).thenAnswer(inv -> {
            MemberVoucher v = inv.getArgument(0);
            saved.add(v);
            return v;
        });
    }

    @Test
    @DisplayName("Hạng GOLD lần đầu: phát đúng 4 mã ship + 2 mã giảm đơn theo rule của hạng")
    void firstIssue_followsTierRules() {
        MemberSubscription gold = subscription(MembershipTier.GOLD, null);

        int issued = service.issueForSubscription(gold);

        assertThat(issued).isEqualTo(6);
        assertThat(saved).filteredOn(v -> v.getType() == VoucherType.SHIPPING).hasSize(4);
        assertThat(saved).filteredOn(v -> v.getType() == VoucherType.ORDER_DISCOUNT).hasSize(2);
        assertThat(gold.getLastVoucherIssuedAt()).isNotNull();
    }

    @Test
    @DisplayName("Chạy lại trong cùng tháng: KHÔNG phát thêm mã nào (idempotent)")
    void secondRunInSameMonth_issuesNothing() {
        // Đã phát trong tháng này: lastVoucherIssuedAt trong tháng + đã có mã của từng mẫu
        MemberSubscription gold = subscription(MembershipTier.GOLD,
                VnTimeWindow.thisMonth().from().plusSeconds(60));
        when(memberVoucherRepository
                .countBySourceSubscription_IdAndVoucherTemplate_IdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(
                        any(), any(), any(), any()))
                .thenReturn(4L);

        int issued = service.issueForSubscription(gold);

        assertThat(issued).isZero();
        verify(memberVoucherRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lượt phát trước lỗi giữa đường (mẫu A đã phát, mẫu B chưa) -> chỉ phát bù mẫu còn thiếu")
    void partialPreviousRun_issuesOnlyTheMissingTemplate() {
        MemberSubscription gold = subscription(MembershipTier.GOLD,
                VnTimeWindow.thisMonth().from().plusSeconds(60));
        // Mẫu ship đã phát đủ 4, mẫu giảm đơn chưa phát mã nào
        when(memberVoucherRepository
                .countBySourceSubscription_IdAndVoucherTemplate_IdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(
                        any(), org.mockito.ArgumentMatchers.eq(shippingTemplate.getId()), any(), any()))
                .thenReturn(4L);
        when(memberVoucherRepository
                .countBySourceSubscription_IdAndVoucherTemplate_IdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(
                        any(), org.mockito.ArgumentMatchers.eq(orderDiscountTemplate.getId()), any(), any()))
                .thenReturn(0L);

        int issued = service.issueForSubscription(gold);

        assertThat(issued).isEqualTo(2);
        assertThat(saved).allMatch(v -> v.getType() == VoucherType.ORDER_DISCOUNT);
    }

    @Test
    @DisplayName("Hạng SILVER nhận ít mã hơn GOLD - số lượng đọc từ rule, không hard-code")
    void silverGetsFewerVouchersThanGold() {
        int silver = service.issueForSubscription(subscription(MembershipTier.SILVER, null));
        assertThat(silver).isEqualTo(3);   // 2 ship + 1 giảm đơn
    }

    @Test
    @DisplayName("Hạng FREE không có rule nào -> không phát mã, nhưng vẫn ghi mốc để job khỏi quét lại")
    void freeTier_getsNothing() {
        MemberSubscription free = subscription(MembershipTier.FREE, null);

        int issued = service.issueForSubscription(free);

        assertThat(issued).isZero();
        verify(memberVoucherRepository, never()).save(any());
        assertThat(free.getLastVoucherIssuedAt()).isNotNull();
    }

    @Test
    @DisplayName("Mã snapshot toàn bộ luật từ template: Admin sửa mẫu sau đó không đổi mã đã phát")
    void voucherSnapshotsRulesFromTemplate() {
        service.issueForSubscription(subscription(MembershipTier.GOLD, null));

        MemberVoucher percentVoucher = saved.stream()
                .filter(v -> v.getType() == VoucherType.ORDER_DISCOUNT)
                .findFirst().orElseThrow();

        assertThat(percentVoucher.getDiscountKind()).isEqualTo(VoucherDiscountKind.PERCENT);
        assertThat(percentVoucher.getDiscountValue()).isEqualByComparingTo("0.10");
        assertThat(percentVoucher.getMaxDiscountAmount()).isEqualByComparingTo("50000");
        assertThat(percentVoucher.getStatus()).isEqualTo(MemberVoucherStatus.UNUSED);
        assertThat(percentVoucher.getSourceSubscription()).isNotNull();
    }

    @Test
    @DisplayName("Mã có tiền tố theo loại và không trùng nhau trong cùng lượt phát")
    void codesArePrefixedAndUnique() {
        service.issueForSubscription(subscription(MembershipTier.DIAMOND, null));

        assertThat(saved).filteredOn(v -> v.getType() == VoucherType.SHIPPING)
                .allMatch(v -> v.getCode().startsWith("SHIP-"));
        assertThat(saved).filteredOn(v -> v.getType() == VoucherType.ORDER_DISCOUNT)
                .allMatch(v -> v.getCode().startsWith("SALE-"));
        assertThat(saved.stream().map(MemberVoucher::getCode).distinct().count())
                .isEqualTo(saved.size());
    }

    @Test
    @DisplayName("Mã hết hạn vào CUỐI ngày thứ validDays, không phải đúng giờ phút lúc phát - "
            + "mã phát 23:50 không được mất gần một ngày")
    void expiryIsEndOfDay() {
        service.issueForSubscription(subscription(MembershipTier.GOLD, null));

        MemberVoucher voucher = saved.get(0);
        java.time.LocalTime expiryTime = voucher.getExpiresAt()
                .atZone(VnTimeWindow.VN_ZONE).toLocalTime();
        assertThat(expiryTime.getHour()).isEqualTo(23);
        assertThat(expiryTime.getMinute()).isEqualTo(59);
    }

    @Test
    @DisplayName("Mẫu đã tắt (active = false) không phát mã mới")
    void inactiveTemplate_isSkipped() {
        when(voucherTemplateRepository.findAllByActiveTrue()).thenReturn(List.of(shippingTemplate));

        int issued = service.issueForSubscription(subscription(MembershipTier.GOLD, null));

        assertThat(issued).isEqualTo(4);
        ArgumentCaptor<MemberVoucher> captor = ArgumentCaptor.forClass(MemberVoucher.class);
        verify(memberVoucherRepository, times(4)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(v -> v.getType() == VoucherType.SHIPPING);
    }

    // ================================================================ Helper

    private MemberSubscription subscription(MembershipTier tier, Instant lastIssuedAt) {
        return MemberSubscription.builder()
                .id(UUID.randomUUID())
                .member(member)
                .tier(tier)
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(Instant.now().minusSeconds(3600))
                .expiresAt(Instant.now().plusSeconds(86400 * 30))
                .lastVoucherIssuedAt(lastIssuedAt)
                .build();
    }

    private static VoucherTemplate template(String name, VoucherType type, VoucherDiscountKind kind,
                                             String value, String maxDiscount,
                                             int silver, int gold, int diamond) {
        VoucherTemplate t = VoucherTemplate.builder()
                .id(UUID.randomUUID())
                .name(name)
                .description(name)
                .type(type)
                .discountKind(kind)
                .discountValue(new BigDecimal(value))
                .maxDiscountAmount(maxDiscount == null ? null : new BigDecimal(maxDiscount))
                .minOrderAmount(BigDecimal.ZERO)
                .validDays(30)
                .active(true)
                .build();
        t.getTierRules().add(rule(t, MembershipTier.SILVER, silver));
        t.getTierRules().add(rule(t, MembershipTier.GOLD, gold));
        t.getTierRules().add(rule(t, MembershipTier.DIAMOND, diamond));
        return t;
    }

    private static VoucherTemplateTierRule rule(VoucherTemplate t, MembershipTier tier, int quantity) {
        return VoucherTemplateTierRule.builder()
                .id(UUID.randomUUID())
                .voucherTemplate(t)
                .tier(tier)
                .quantityPerMonth(quantity)
                .build();
    }
}
