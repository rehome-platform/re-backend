package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateWarehouseRequest;
import com.rehome.rehome.dto.request.UpdateWarehouseRequest;
import com.rehome.rehome.dto.response.WarehouseAdminResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CatalogStatus;

import java.util.List;
import java.util.UUID;

public interface WarehouseAdminService {

    /** API 141: danh sách kho gồm cả kho HIDDEN, lọc theo status nếu có. */
    List<WarehouseAdminResponse> list(CatalogStatus status);

    /** API 142: tạo kho mới (ACTIVE), mã kho tự sinh. */
    WarehouseAdminResponse create(Account admin, CreateWarehouseRequest request);

    /** API 143: sửa thông tin kho. */
    WarehouseAdminResponse update(Account admin, UUID warehouseId, UpdateWarehouseRequest request);

    /** API 144: ẩn kho; 409 WAREHOUSE_HAS_STOCK nếu kho còn hàng hoặc phiếu ký gửi đang xử lý. */
    WarehouseAdminResponse hide(Account admin, UUID warehouseId);
}
