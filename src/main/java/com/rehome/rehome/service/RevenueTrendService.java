package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.RevenueTrendResponse;

public interface RevenueTrendService {

    /**
     * Doanh thu nền tảng (hoa hồng + phí rút hàng) theo ngày/tuần/tháng trong [from, to] (yyyy-MM-dd, giờ VN).
     * granularity: day (mặc định) | week | month. Bỏ trống to = hôm nay; bỏ trống from = từ ngày có doanh thu
     * đầu tiên (không có doanh thu nào thì lấy 30 ngày gần nhất).
     */
    RevenueTrendResponse trend(String from, String to, String granularity);
}
