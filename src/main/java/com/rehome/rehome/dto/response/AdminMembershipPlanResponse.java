package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MembershipTier;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminMembershipPlanResponse {

    private String id;
    private MembershipTier tier;
    private String name;
    private String description;
    private BigDecimal pricePerMonth;
    private BigDecimal pricePerYear;
    private Integer liveTryonPerDay;
    private Integer liveTryonPerMonth;
    private Integer liveTryonMaxSeconds;
    private Integer aiTryonPerDay;
    private Integer aiTryonPerMonth;
    private Boolean featuredListing;
    private Integer maxPendingConsignmentForms;
    private Boolean purchasable;
    private Boolean active;
    private Integer sortOrder;
    private long activeSubscriberCount;
    private Instant updatedAt;
}
