package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.RevenueItemResponse;

public interface AdminRevenueService {

    /**
     * Danh sách món đã bán (đơn COMPLETED) có phân trang. from/to (yyyy-MM-dd, gồm cả ngày to) lọc theo ngày
     * đơn hoàn thành, term lọc theo kỳ ký gửi áp dụng lúc đặt đơn; đều tùy chọn.
     */
    PageResponse<RevenueItemResponse> listSoldItems(String from, String to, Integer term, int page, int size);
}
