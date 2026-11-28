package com.rehome.rehome.dto.response;

import java.util.UUID;

public record WarehouseNearestResponse(
        UUID id,
        String name,
        String address,
        double distanceKm,
        String openingHours
) {
    public WarehouseNearestResponse {
        distanceKm = Math.round(distanceKm * 10.0) / 10.0;
    }
}