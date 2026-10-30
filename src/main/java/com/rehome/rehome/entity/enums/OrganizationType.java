package com.rehome.rehome.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Loại hình tổ chức cho form đăng ký (GET /api/organizations/options).
 * OrganizationProfile.orgType vẫn lưu dạng String; code ở đây chính là name() của enum.
 * DANH SÁCH TẠM: thay bằng danh sách mã mà app đang dùng để hai bên khớp nhau.
 */
@Getter
@RequiredArgsConstructor
public enum OrganizationType {
    CHARITY_FUND("Quỹ từ thiện"),
    NGO("Tổ chức phi chính phủ"),
    SCHOOL_CLUB("Câu lạc bộ trường học"),
    VOLUNTEER_GROUP("Nhóm tình nguyện / cộng đồng"),
    SOCIAL_PROTECTION_CENTER("Cơ sở bảo trợ xã hội"),
    RELIGIOUS("Cơ sở tôn giáo"),
    OTHER("Khác");

    private final String displayName;
}
