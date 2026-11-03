package com.rehome.rehome.dto.response;

import java.util.UUID;

/**
 * Response item cho GET /api/organizations/nearby. Chỉ tổ chức đã VERIFIED (F-082).
 * distanceKm là khoảng cách đường chim bay từ vị trí Member đến điểm nhận đồ, làm tròn 1 chữ số.
 */
public record OrganizationNearbyResponse(
        UUID id,
        String displayName,
        String logoUrl,
        String donationPointAddress,
        double distanceKm
) {
    public OrganizationNearbyResponse {
        distanceKm = Math.round(distanceKm * 10.0) / 10.0;
    }
}
