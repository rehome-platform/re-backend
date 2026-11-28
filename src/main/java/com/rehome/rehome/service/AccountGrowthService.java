package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AccountGrowthResponse;
import com.rehome.rehome.entity.enums.Role;

public interface AccountGrowthService {

    AccountGrowthResponse growth(String from, String to, String granularity, Role role);
}
