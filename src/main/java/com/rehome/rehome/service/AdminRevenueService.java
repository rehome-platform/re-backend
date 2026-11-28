package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.RevenueItemResponse;

public interface AdminRevenueService {

    PageResponse<RevenueItemResponse> listSoldItems(String from, String to, Integer term, int page, int size);
}
