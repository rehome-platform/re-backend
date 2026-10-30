package com.rehome.rehome.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Phạm vi hoạt động cho form đăng ký (GET /api/organizations/options).
 * OrganizationProfile.operationScope vẫn lưu dạng String; code ở đây chính là name() của enum.
 * DANH SÁCH TẠM: thay bằng danh sách mã mà app đang dùng để hai bên khớp nhau.
 */
@Getter
@RequiredArgsConstructor
public enum OperationScope {
    NATIONAL("Toàn quốc"),
    PROVINCE("Cấp tỉnh / thành phố"),
    DISTRICT("Cấp quận / huyện"),
    WARD("Cấp phường / xã");

    private final String displayName;
}
