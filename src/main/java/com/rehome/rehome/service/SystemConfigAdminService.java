package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.SystemConfigResponse;
import com.rehome.rehome.entity.auth.Account;

public interface SystemConfigAdminService {

    // API 146 - F-157: sửa một tham số theo key. Chỉ áp dụng cho giao dịch phát sinh sau khi sửa
    SystemConfigResponse update(Account admin, String key, String rawValue);
}
