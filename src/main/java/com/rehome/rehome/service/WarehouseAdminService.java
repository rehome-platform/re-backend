package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateWarehouseRequest;
import com.rehome.rehome.dto.request.UpdateWarehouseRequest;
import com.rehome.rehome.dto.response.WarehouseAdminResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CatalogStatus;

import java.util.List;
import java.util.UUID;

public interface WarehouseAdminService {

    List<WarehouseAdminResponse> list(CatalogStatus status);

    WarehouseAdminResponse create(Account admin, CreateWarehouseRequest request);

    WarehouseAdminResponse update(Account admin, UUID warehouseId, UpdateWarehouseRequest request);

    WarehouseAdminResponse hide(Account admin, UUID warehouseId);

    WarehouseAdminResponse unhide(Account admin, UUID warehouseId);
}
