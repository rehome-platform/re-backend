package com.rehome.rehome.entity.enums;

public enum ReturnOnsiteResult {
    FAIL,                 // không đạt kiểm định
    SUSPECTED_FAKE,       // nghi hàng giả
    SUSPECTED_ILLEGAL,    // nghi hàng cấm/bất hợp pháp
    MEMBER_DECLINED       // Member từ chối giá đề xuất sau kiểm định (tên này là mình đặt, spec chưa ghi)
}
