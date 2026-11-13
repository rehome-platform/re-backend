package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.VoucherType;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /memberships/plans (public) - danh sách gói đang bán, sắp theo sortOrder.
 *
 * <p>Mobile phải render quyền lợi <b>từ JSON này</b>, không hard-code text, vì Admin sửa được mọi
 * con số ở màn W-01. Hạn mức {@code null} nghĩa là KHÔNG GIỚI HẠN -> hiện "Không giới hạn".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipPlanResponse {

    private String id;
    private MembershipTier tier;
    private String name;
    private String description;
    private BigDecimal pricePerMonth;
    // null = không bán gói năm
    private BigDecimal pricePerYear;
    // Tiết kiệm bao nhiêu % khi trả theo năm so với trả 12 tháng; null khi không bán gói năm
    private BigDecimal yearlySavingPercent;
    private Benefits benefits;
    private List<VoucherBenefit> vouchers;
    private Integer sortOrder;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Benefits {
        private Integer liveTryonPerDay;
        private Integer liveTryonPerMonth;
        private Integer liveTryonMaxSeconds;
        private Integer aiTryonPerDay;
        private Integer aiTryonPerMonth;
        private Boolean featuredListing;
        private Integer maxPendingConsignmentForms;
    }

    /** Một dòng "mỗi tháng nhận N mã loại X", dựng từ voucher_template_tier_rules của hạng đó. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VoucherBenefit {
        private String name;
        private VoucherType type;
        private Integer quantityPerMonth;
    }
}
