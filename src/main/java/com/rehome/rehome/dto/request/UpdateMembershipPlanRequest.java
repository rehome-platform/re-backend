package com.rehome.rehome.dto.request;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PUT /api/admin/membership-plans/{id} (màn W-01).
 *
 * <p>Hạn mức để {@code null} nghĩa là KHÔNG GIỚI HẠN (checkbox "Không giới hạn" ở form), nên
 * không phân biệt được "không gửi field" với "gửi null" - đây là chủ ý: form W-01 luôn gửi đủ
 * mọi field.
 *
 * <p>Dòng {@code FREE}: {@code pricePerMonth} / {@code pricePerYear} gửi lên bị bỏ qua, service
 * luôn ép 0 / null.
 */
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
