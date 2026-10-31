package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hồ sơ tổ chức sau khi cập nhật thông tin thường (F-096).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationProfileUpdateResponse {
    private String id;
    private String logoUrl;
    private String description;
    private String contactPhone;
    private String donationPointReceivingHours;
}
