package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.WarehouseNearestResponse;
import com.rehome.rehome.dto.marketplace.response.WarehouseResponse;
import java.util.List;

public interface WarehouseService {
    List<WarehouseNearestResponse> getNearest(double lat, double lng);
    List<WarehouseResponse> getActiveWarehouses();
}
