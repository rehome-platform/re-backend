package com.rehome.rehome.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OperationScope {
    NATIONAL("Toàn quốc"),
    PROVINCE("Cấp tỉnh / thành phố"),
    DISTRICT("Cấp quận / huyện"),
    WARD("Cấp phường / xã");

    private final String displayName;
}
