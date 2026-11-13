package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.dto.request.VoucherTemplateRequest;
import com.rehome.rehome.dto.response.VoucherTemplateResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import com.rehome.rehome.entity.membership.VoucherTemplate;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.VoucherTemplateRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Validate mẫu voucher. Luật quan trọng nhất: <b>{@code ORDER_DISCOUNT} bắt buộc {@code PERCENT}</b>.
 *
 * <p>Đây không phải quy ước form mà là ràng buộc nghiệp vụ: mức giảm của voucher giảm đơn bị chặn
 * bởi hoa hồng nền tảng thu được theo kỳ ký gửi hiện tại. Một mã FIXED 50.000đ áp lên món kỳ 1 giá
 * 200.000đ (hoa hồng 20.000đ) sẽ làm nền tảng lỗ trên đơn đó; dùng PERCENT thì mức giảm tự co theo
 * giá hàng.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherTemplateAdminServiceImplTest {

    @Mock private VoucherTemplateRepository voucherTemplateRepository;
    @Mock private AuditLogger auditLogger;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private VoucherTemplateAdminServiceImpl service;

    private Account admin;

    @BeforeEach
    void setUp() {
        admin = Account.builder().id(UUID.randomUUID()).email("admin@example.com")
                .role(Role.ADMIN).build();
        when(voucherTemplateRepository.save(any(VoucherTemplate.class))).thenAnswer(inv -> {
            VoucherTemplate t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
    }

    @Test
    @DisplayName("ORDER_DISCOUNT + FIXED -> VOUCHER_TEMPLATE_INVALID 400. "
            + "FIXED chỉ dùng được cho SHIPPING")
    void orderDiscountMustBePercent() {
        VoucherTemplateRequest request = request(VoucherType.ORDER_DISCOUNT,
                VoucherDiscountKind.FIXED, "20000", null);
        assertInvalid(request);
    }

    @Test
    @DisplayName("PERCENT mà thiếu maxDiscountAmount -> 400. Không có trần thì đơn 10 triệu giảm 1 triệu")
    void percentWithoutCap_isRejected() {
        VoucherTemplateRequest request = request(VoucherType.ORDER_DISCOUNT,
                VoucherDiscountKind.PERCENT, "0.10", null);
        assertInvalid(request);
    }

    @Test
    @DisplayName("PERCENT ngoài khoảng (0, 1] -> 400 (0.10 = 10%, trên 1 là giảm hơn 100% giá hàng)")
    void percentOutOfRange_isRejected() {
        assertInvalid(request(VoucherType.ORDER_DISCOUNT, VoucherDiscountKind.PERCENT, "1.5", "50000"));
        assertInvalid(request(VoucherType.ORDER_DISCOUNT, VoucherDiscountKind.PERCENT, "0", "50000"));
    }

    @Test
    @DisplayName("SHIPPING + FIXED là hợp lệ - phí ship không liên quan hoa hồng, trần tự nhiên là "
            + "chính phí ship thật của đơn")
    void shippingFixed_isAccepted() {
        VoucherTemplateRequest request = request(VoucherType.SHIPPING,
                VoucherDiscountKind.FIXED, "15000", null);

        VoucherTemplateResponse response = service.create(admin, request);

        assertThat(response.getType()).isEqualTo(VoucherType.SHIPPING);
        assertThat(response.getDiscountKind()).isEqualTo(VoucherDiscountKind.FIXED);
        // FIXED không giữ maxDiscountAmount dù client có gửi lên
        assertThat(response.getMaxDiscountAmount()).isNull();
        verify(auditLogger).log(any(), org.mockito.ArgumentMatchers.eq("UPDATE_VOUCHER_TEMPLATE"),
                org.mockito.ArgumentMatchers.eq("VOUCHER_TEMPLATE"), any(), any());
    }

    @Test
    @DisplayName("ORDER_DISCOUNT + PERCENT + có trần là hợp lệ")
    void orderDiscountPercentWithCap_isAccepted() {
        VoucherTemplateResponse response = service.create(admin, request(
                VoucherType.ORDER_DISCOUNT, VoucherDiscountKind.PERCENT, "0.10", "50000"));

        assertThat(response.getDiscountValue()).isEqualByComparingTo("0.10");
        assertThat(response.getMaxDiscountAmount()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("tierRules chứa FREE -> 400. Hạng miễn phí không nhận voucher")
    void freeTierRule_isRejected() {
        VoucherTemplateRequest request = request(VoucherType.SHIPPING,
                VoucherDiscountKind.FIXED, "15000", null);
        request.setTierRules(List.of(new VoucherTemplateRequest.TierRule(MembershipTier.FREE, 1)));
        assertInvalid(request);
    }

    @Test
    @DisplayName("tierRules trùng tier -> 400 (một hạng chỉ có một dòng rule cho mỗi mẫu)")
    void duplicateTierRule_isRejected() {
        VoucherTemplateRequest request = request(VoucherType.SHIPPING,
                VoucherDiscountKind.FIXED, "15000", null);
        request.setTierRules(List.of(
                new VoucherTemplateRequest.TierRule(MembershipTier.GOLD, 1),
                new VoucherTemplateRequest.TierRule(MembershipTier.GOLD, 2)));
        assertInvalid(request);
    }

    @Test
    @DisplayName("quantityPerMonth ngoài [1, 10] -> 400")
    void quantityOutOfRange_isRejected() {
        VoucherTemplateRequest zero = request(VoucherType.SHIPPING, VoucherDiscountKind.FIXED, "15000", null);
        zero.setTierRules(List.of(new VoucherTemplateRequest.TierRule(MembershipTier.GOLD, 0)));
        assertInvalid(zero);

        VoucherTemplateRequest tooMany = request(VoucherType.SHIPPING, VoucherDiscountKind.FIXED, "15000", null);
        tooMany.setTierRules(List.of(new VoucherTemplateRequest.TierRule(MembershipTier.GOLD, 11)));
        assertInvalid(tooMany);
    }

    @Test
    @DisplayName("validDays ngoài [1, 365] -> 400")
    void validDaysOutOfRange_isRejected() {
        VoucherTemplateRequest request = request(VoucherType.SHIPPING, VoucherDiscountKind.FIXED, "15000", null);
        request.setValidDays(0);
        assertInvalid(request);

        request.setValidDays(400);
        assertInvalid(request);
    }

    @Test
    @DisplayName("PUT thay thế TOÀN BỘ tierRules: hạng không còn trong danh sách là hạng ngừng nhận mẫu")
    void updateReplacesAllTierRules() {
        VoucherTemplate existing = VoucherTemplate.builder()
                .id(UUID.randomUUID())
                .name("Miễn phí vận chuyển")
                .type(VoucherType.SHIPPING)
                .discountKind(VoucherDiscountKind.FIXED)
                .discountValue(new BigDecimal("15000"))
                .minOrderAmount(BigDecimal.ZERO)
                .validDays(30)
                .active(true)
                .build();
        existing.getTierRules().add(com.rehome.rehome.entity.membership.VoucherTemplateTierRule.builder()
                .id(UUID.randomUUID()).voucherTemplate(existing)
                .tier(MembershipTier.SILVER).quantityPerMonth(2).build());
        when(voucherTemplateRepository.findWithTierRulesById(existing.getId()))
                .thenReturn(Optional.of(existing));

        VoucherTemplateRequest request = request(VoucherType.SHIPPING, VoucherDiscountKind.FIXED, "15000", null);
        request.setTierRules(List.of(new VoucherTemplateRequest.TierRule(MembershipTier.DIAMOND, 6)));

        VoucherTemplateResponse response = service.update(admin, existing.getId(), request);

        assertThat(response.getTierRules()).hasSize(1);
        assertThat(response.getTierRules().get(0).getTier()).isEqualTo(MembershipTier.DIAMOND);
        assertThat(response.getTierRules().get(0).getQuantityPerMonth()).isEqualTo(6);
    }

    @Test
    @DisplayName("Ngừng phát chỉ tắt active, KHÔNG xóa mẫu - member_vouchers trỏ FK vào đây")
    void deactivateKeepsTheTemplate() {
        VoucherTemplate existing = VoucherTemplate.builder()
                .id(UUID.randomUUID())
                .name("Giảm giá đơn hàng")
                .type(VoucherType.ORDER_DISCOUNT)
                .discountKind(VoucherDiscountKind.PERCENT)
                .discountValue(new BigDecimal("0.10"))
                .maxDiscountAmount(new BigDecimal("50000"))
                .minOrderAmount(BigDecimal.ZERO)
                .validDays(30)
                .active(true)
                .build();
        when(voucherTemplateRepository.findWithTierRulesById(existing.getId()))
                .thenReturn(Optional.of(existing));

        VoucherTemplateResponse response = service.deactivate(admin, existing.getId());

        assertThat(response.getActive()).isFalse();
        verify(voucherTemplateRepository).save(existing);
    }

    @Test
    @DisplayName("Mẫu không tồn tại -> VOUCHER_TEMPLATE_NOT_FOUND 404")
    void unknownTemplate_isNotFound() {
        UUID unknown = UUID.randomUUID();
        when(voucherTemplateRepository.findWithTierRulesById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(admin, unknown))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.VOUCHER_TEMPLATE_NOT_FOUND);
    }

    @Test
    @DisplayName("tierRules để trống là hợp lệ: mẫu tồn tại nhưng tạm thời không hạng nào nhận")
    void emptyTierRules_isAccepted() {
        VoucherTemplateRequest request = request(VoucherType.SHIPPING, VoucherDiscountKind.FIXED, "15000", null);
        request.setTierRules(List.of());

        assertThatCode(() -> service.create(admin, request)).doesNotThrowAnyException();
    }

    // ================================================================ Helper

    private void assertInvalid(VoucherTemplateRequest request) {
        assertThatThrownBy(() -> service.create(admin, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.VOUCHER_TEMPLATE_INVALID);
    }

    private static VoucherTemplateRequest request(VoucherType type, VoucherDiscountKind kind,
                                                   String value, String maxDiscount) {
        VoucherTemplateRequest request = new VoucherTemplateRequest();
        request.setName("Mẫu thử");
        request.setDescription("Mô tả");
        request.setType(type);
        request.setDiscountKind(kind);
        request.setDiscountValue(new BigDecimal(value));
        request.setMaxDiscountAmount(maxDiscount == null ? null : new BigDecimal(maxDiscount));
        request.setMinOrderAmount(BigDecimal.ZERO);
        request.setValidDays(30);
        request.setActive(true);
        request.setTierRules(List.of(
                new VoucherTemplateRequest.TierRule(MembershipTier.SILVER, 2),
                new VoucherTemplateRequest.TierRule(MembershipTier.GOLD, 4)));
        return request;
    }
}
