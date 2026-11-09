package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.SystemConfigItemResponse;
import com.rehome.rehome.dto.response.SystemConfigResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.List;

public interface SystemConfigAdminService {

    // F-156: toàn bộ tham số trong catalog kèm giá trị đang áp dụng (DB nếu có dòng, không thì mặc định)
    List<SystemConfigItemResponse> list();

    // API 146 - F-157: sửa một tham số theo key. Chỉ áp dụng cho giao dịch phát sinh sau khi sửa
    SystemConfigResponse update(Account admin, String key, String rawValue);
}