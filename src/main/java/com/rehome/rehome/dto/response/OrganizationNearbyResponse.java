package com.rehome.rehome.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
        List<OrganizationNearbyCampaignResponse> activeCampaigns
) {
    public OrganizationNearbyResponse {
        distanceKm = Math.round(distanceKm * 10.0) / 10.0;
    }
}
