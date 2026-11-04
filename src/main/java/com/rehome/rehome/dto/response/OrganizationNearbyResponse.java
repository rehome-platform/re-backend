package com.rehome.rehome.dto.response;

import java.math.BigDecimal;
import java.util.List;
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
        BigDecimal donationPointLatitude,
        BigDecimal donationPointLongitude,
        String donationPointReceivingHours,
        String orgType,
        double distanceKm,
        // Chiến dịch OPEN của tổ chức - cho bản đồ cộng đồng biết tổ chức đang kêu gọi gì
        List<OrganizationNearbyCampaignResponse> activeCampaigns
) {
    public OrganizationNearbyResponse {
        distanceKm = Math.round(distanceKm * 10.0) / 10.0;
    }
}
