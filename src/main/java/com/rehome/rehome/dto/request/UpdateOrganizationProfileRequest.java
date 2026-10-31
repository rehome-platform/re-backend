package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin thường của tổ chức (F-096) — áp dụng ngay, không qua Moderator.
 * PATCH: field nào bỏ trống thì giữ nguyên giá trị cũ.
 * Thông tin pháp lý (tên, người đại diện, giấy tờ) đi qua luồng riêng F-097.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrganizationProfileRequest {
    private String logoUrl;
    private String description;
    private String contactPhone;
    private String donationPointReceivingHours;
}
