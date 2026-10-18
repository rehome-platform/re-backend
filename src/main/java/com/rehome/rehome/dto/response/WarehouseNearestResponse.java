package com.rehome.rehome.dto.response;

import java.util.UUID;

/**
 * Response item cho GET /warehouses/nearest (F-015).
 * distanceKm là khoảng cách đường chim bay từ vị trí Member đến kho, làm tròn 1 chữ số thập phân.
 */
public record WarehouseNearestResponse(
        UUID id,
        String name,
        String address,
        double distanceKm
) {
    // Compact constructor: tự làm tròn 1 chữ số thập phân (1.84 -> 1.8)
    public WarehouseNearestResponse {
        distanceKm = Math.round(distanceKm * 10.0) / 10.0;
    }
}