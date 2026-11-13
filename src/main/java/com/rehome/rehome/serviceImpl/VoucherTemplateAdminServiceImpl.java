package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.dto.request.VoucherTemplateRequest;
import com.rehome.rehome.dto.response.VoucherTemplateResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import com.rehome.rehome.entity.membership.VoucherTemplate;
import com.rehome.rehome.entity.membership.VoucherTemplateTierRule;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.VoucherTemplateRepository;
import com.rehome.rehome.service.VoucherTemplateAdminService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Admin quản lý mẫu voucher (màn W-04).
 *
 * <h2>Luật bắt buộc không được nới</h2>
 * {@code type = ORDER_DISCOUNT} <b>phải</b> là {@code PERCENT}. Lý do nằm ở nghiệp vụ, không phải
 * ở form: mức giảm của voucher giảm đơn bị chặn bởi hoa hồng nền tảng thu được theo kỳ ký gửi hiện
 * tại của món (kỳ 1 = 10%, kỳ 2 = 15%, kỳ 3 = 20%). Một mã FIXED 50.000đ áp lên món kỳ 1 giá
 * 200.000đ sẽ vượt hoa hồng 20.000đ và nền tảng lỗ trên đơn đó. Dùng PERCENT thì mức giảm tự co
 * theo giá hàng, và {@code VoucherCalculator} vẫn chặn thêm một lớp bằng trần hoa hồng.
 *
 * <p>{@code FIXED} chỉ dùng cho {@code SHIPPING}: phí ship là tiền trả cho GHN, không liên quan
 * hoa hồng, và trần tự nhiên của nó là chính phí ship thật của đơn.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoucherTemplateAdminServiceImpl implements VoucherTemplateAdminService {

    private static final int MIN_VALID_DAYS = 1;
    private static final int MAX_VALID_DAYS = 365;
    private static final int MIN_QUANTITY_PER_MONTH = 1;
    private static final int MAX_QUANTITY_PER_MONTH = 10;

    private final VoucherTemplateRepository voucherTemplateRepository;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    @Override
    public List<VoucherTemplateResponse> list() {
        List<VoucherTemplateResponse> result = new ArrayList<>();
        for (VoucherTemplate t : voucherTemplateRepository.findAllByOrderByCreatedAtAsc()) {
            result.add(toResponse(t));
        }
        return result;
    }

    @Override
    @Transactional
    public VoucherTemplateResponse create(Account admin, VoucherTemplateRequest request) {
        validate(request);

        VoucherTemplate template = new VoucherTemplate();
        apply(template, request);
        voucherTemplateRepository.save(template);

        audit(admin, template, "CREATE");
        return toResponse(template);
    }

    @Override
    @Transactional
    public VoucherTemplateResponse update(Account admin, UUID id, VoucherTemplateRequest request) {
        validate(request);

        VoucherTemplate template = voucherTemplateRepository.findWithTierRulesById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_TEMPLATE_NOT_FOUND));
        apply(template, request);
        voucherTemplateRepository.save(template);

        // Mã đã phát KHÔNG đổi theo: MemberVoucher snapshot luật từ template lúc phát, nên sửa ở
        // đây chỉ ảnh hưởng mã phát từ lúc này trở đi. Đây là chủ ý.
        audit(admin, template, "UPDATE");
        return toResponse(template);
    }

    @Override
    @Transactional
    public VoucherTemplateResponse deactivate(Account admin, UUID id) {
        VoucherTemplate template = voucherTemplateRepository.findWithTierRulesById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_TEMPLATE_NOT_FOUND));
        template.setActive(false);
        voucherTemplateRepository.save(template);

        audit(admin, template, "DEACTIVATE");
        return toResponse(template);
    }

    // ================================================================ Validate

    private void validate(VoucherTemplateRequest r) {
        if (r == null || !StringUtils.hasText(r.getName())
                || r.getType() == null || r.getDiscountKind() == null || r.getDiscountValue() == null) {
            throw new AppException(ErrorCode.VOUCHER_TEMPLATE_INVALID);
        }

        // Luật cốt lõi - xem javadoc của class
        if (r.getType() == VoucherType.ORDER_DISCOUNT && r.getDiscountKind() != VoucherDiscountKind.PERCENT) {
            throw new AppException(ErrorCode.VOUCHER_TEMPLATE_INVALID);
        }

        if (r.getDiscountKind() == VoucherDiscountKind.PERCENT) {
            // (0, 1] - 0.10 = 10%. Trên 1 nghĩa là giảm hơn 100% giá hàng.
            if (r.getDiscountValue().signum() <= 0 || r.getDiscountValue().compareTo(BigDecimal.ONE) > 0) {
                throw new AppException(ErrorCode.VOUCHER_TEMPLATE_INVALID);
            }
            // PERCENT không có trần là mã không chặn được: đơn 10 triệu giảm 10% = 1 triệu
            if (r.getMaxDiscountAmount() == null || r.getMaxDiscountAmount().signum() <= 0) {
                throw new AppException(ErrorCode.VOUCHER_TEMPLATE_INVALID);
            }
        } else if (r.getDiscountValue().signum() <= 0) {
            throw new AppException(ErrorCode.VOUCHER_TEMPLATE_INVALID);
        }

        if (r.getMinOrderAmount() != null && r.getMinOrderAmount().signum() < 0) {
            throw new AppException(ErrorCode.VOUCHER_TEMPLATE_INVALID);
        }
        if (r.getValidDays() == null
                || r.getValidDays() < MIN_VALID_DAYS || r.getValidDays() > MAX_VALID_DAYS) {
            throw new AppException(ErrorCode.VOUCHER_TEMPLATE_INVALID);
        }

        Set<MembershipTier> seen = EnumSet.noneOf(MembershipTier.class);
        for (VoucherTemplateRequest.TierRule rule : safeRules(r)) {
            if (rule.getTier() == null || rule.getTier() == MembershipTier.FREE) {
                throw new AppException(ErrorCode.VOUCHER_TEMPLATE_INVALID);
            }
            if (!seen.add(rule.getTier())) {
                throw new AppException(ErrorCode.VOUCHER_TEMPLATE_INVALID);
            }
            if (rule.getQuantityPerMonth() == null
                    || rule.getQuantityPerMonth() < MIN_QUANTITY_PER_MONTH
                    || rule.getQuantityPerMonth() > MAX_QUANTITY_PER_MONTH) {
                throw new AppException(ErrorCode.VOUCHER_TEMPLATE_INVALID);
            }
        }
    }

    private static List<VoucherTemplateRequest.TierRule> safeRules(VoucherTemplateRequest r) {
        return r.getTierRules() == null ? List.of() : r.getTierRules();
    }

    // ================================================================ Map

    private void apply(VoucherTemplate template, VoucherTemplateRequest r) {
        template.setName(r.getName().trim());
        template.setDescription(r.getDescription());
        template.setType(r.getType());
        template.setDiscountKind(r.getDiscountKind());
        template.setDiscountValue(r.getDiscountValue());
        // FIXED không có trần riêng: trần của nó là phí ship thật của đơn
        template.setMaxDiscountAmount(
                r.getDiscountKind() == VoucherDiscountKind.PERCENT ? r.getMaxDiscountAmount() : null);
        template.setMinOrderAmount(r.getMinOrderAmount() == null ? BigDecimal.ZERO : r.getMinOrderAmount());
        template.setValidDays(r.getValidDays());
        template.setActive(r.getActive() == null || r.getActive());

        // PUT thay thế toàn bộ rule: hạng không có trong danh sách là hạng không còn nhận mẫu này.
        // Xoá-rồi-thêm trên cùng collection (orphanRemoval = true) để Hibernate dọn các dòng cũ.
        template.getTierRules().clear();
        for (VoucherTemplateRequest.TierRule rule : safeRules(r)) {
            template.getTierRules().add(VoucherTemplateTierRule.builder()
                    .voucherTemplate(template)
                    .tier(rule.getTier())
                    .quantityPerMonth(rule.getQuantityPerMonth())
                    .build());
        }
    }

    private VoucherTemplateResponse toResponse(VoucherTemplate t) {
        List<VoucherTemplateResponse.TierRule> rules = new ArrayList<>();
        for (VoucherTemplateTierRule r : t.getTierRules()) {
            rules.add(VoucherTemplateResponse.TierRule.builder()
                    .tier(r.getTier())
                    .quantityPerMonth(r.getQuantityPerMonth())
                    .build());
        }
        rules.sort(Comparator.comparingInt(r -> r.getTier().ordinal()));

        return VoucherTemplateResponse.builder()
                .id(t.getId().toString())
                .name(t.getName())
                .description(t.getDescription())
                .type(t.getType())
                .discountKind(t.getDiscountKind())
                .discountValue(t.getDiscountValue())
                .maxDiscountAmount(t.getMaxDiscountAmount())
                .minOrderAmount(t.getMinOrderAmount())
                .validDays(t.getValidDays())
                .active(t.getActive())
                .tierRules(rules)
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private void audit(Account admin, VoucherTemplate t, String action) {
        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("action", action);
        detail.put("name", t.getName());
        detail.put("type", t.getType().name());
        detail.put("discountKind", t.getDiscountKind().name());
        detail.put("discountValue", t.getDiscountValue());
        detail.put("maxDiscountAmount", t.getMaxDiscountAmount());
        detail.put("minOrderAmount", t.getMinOrderAmount());
        detail.put("validDays", t.getValidDays());
        detail.put("active", t.getActive());
        ObjectNode rules = detail.putObject("tierRules");
        for (VoucherTemplateTierRule r : t.getTierRules()) {
            rules.put(r.getTier().name(), r.getQuantityPerMonth());
        }
        auditLogger.log(admin, "UPDATE_VOUCHER_TEMPLATE", "VOUCHER_TEMPLATE",
                t.getId() == null ? null : t.getId().toString(), detail);
    }
}
