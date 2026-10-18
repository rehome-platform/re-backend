package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.WarehouseNearestResponse;

import java.util.List;

public interface WarehouseService {
    List<WarehouseNearestResponse> getNearest(double lat, double lng);
}
