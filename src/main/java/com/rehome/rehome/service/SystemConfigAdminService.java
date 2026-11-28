package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.SystemConfigItemResponse;
import com.rehome.rehome.dto.response.SystemConfigResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.List;

public interface SystemConfigAdminService {

    List<SystemConfigItemResponse> list();

    SystemConfigResponse update(Account admin, String key, String rawValue);
}