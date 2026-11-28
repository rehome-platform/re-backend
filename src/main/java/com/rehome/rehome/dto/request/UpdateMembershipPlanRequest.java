package com.rehome.rehome.dto.request;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMembershipPlanRequest {

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
    private Boolean active;
    private Integer sortOrder;
}
