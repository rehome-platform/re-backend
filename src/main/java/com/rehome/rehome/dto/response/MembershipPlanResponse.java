package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.VoucherType;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private BigDecimal pricePerYear;
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
