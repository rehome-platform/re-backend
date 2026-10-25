package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.MyWarehouseResponse;
import com.rehome.rehome.dto.response.WarehouseNearestResponse;
import com.rehome.rehome.dto.marketplace.response.WarehouseResponse;
import java.util.List;
import java.util.UUID;

public interface WarehouseService {
    List<WarehouseNearestResponse> getNearest(double lat, double lng);
    List<WarehouseResponse> getActiveWarehouses();
    List<MyWarehouseResponse> getManagedWarehouses(UUID accountId);
}
