package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MembershipTier;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/admin/membership-plans - cả 4 dòng kể cả FREE, cho màn W-01.
 *
 * <p>Khác {@link MembershipPlanResponse} (bản cho Member): trả thêm {@code purchasable},
 * {@code active}, {@code activeSubscriberCount} và trả phẳng từng hạn mức thay vì gói trong
 * {@code benefits}, để form sửa map 1-1 với request.
 */
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
    // Số subscription đang ACTIVE của gói này; click sang W-02 đã lọc sẵn hạng đó
    private long activeSubscriberCount;
    private Instant updatedAt;
}
