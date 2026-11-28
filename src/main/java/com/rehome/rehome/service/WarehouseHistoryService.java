package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.WarehouseHistoryResponse;
import com.rehome.rehome.entity.auth.Account;

public interface WarehouseHistoryService {

    PageResponse<WarehouseHistoryResponse> list(Account moderator, String type, int page, int size);
}
