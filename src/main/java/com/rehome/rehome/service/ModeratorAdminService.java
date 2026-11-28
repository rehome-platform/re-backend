package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.ChangeModeratorWarehouseRequest;
import com.rehome.rehome.dto.request.CreateModeratorRequest;
import com.rehome.rehome.dto.response.ModeratorAdminResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.AccountStatus;

import java.util.UUID;

public interface ModeratorAdminService {

    ModeratorAdminResponse create(Account admin, CreateModeratorRequest request);

    PageResponse<ModeratorAdminResponse> list(UUID warehouseId, String search, AccountStatus status,
                                              int page, int size);

    ModeratorAdminResponse changeWarehouse(Account admin, UUID moderatorId, ChangeModeratorWarehouseRequest request);
}
