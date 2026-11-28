package com.rehome.rehome.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
