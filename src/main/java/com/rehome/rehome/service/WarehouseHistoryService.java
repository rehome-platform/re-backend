package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.WarehouseHistoryResponse;
import com.rehome.rehome.entity.auth.Account;

public interface WarehouseHistoryService {

    // Lịch sử thao tác của kho mà Moderator đang thuộc về (kho suy ra từ tài khoản, không nhận từ client)
    PageResponse<WarehouseHistoryResponse> list(Account moderator, String type, int page, int size);
}
