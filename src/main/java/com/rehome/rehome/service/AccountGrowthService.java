package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AccountGrowthResponse;
import com.rehome.rehome.entity.enums.Role;

public interface AccountGrowthService {

    /**
     * Số tài khoản đăng ký theo ngày/tuần/tháng trong [from, to] (yyyy-MM-dd, giờ VN).
     * granularity: day (mặc định) | week | month. Bỏ trống from/to = 30 ngày gần nhất. role tùy chọn.
     */
    AccountGrowthResponse growth(String from, String to, String granularity, Role role);
}
