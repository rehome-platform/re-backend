package com.rehome.rehome.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * 1 chiến dịch đang mở của tổ chức, lồng trong OrganizationNearbyResponse.activeCampaigns.
 */
public record OrganizationNearbyCampaignResponse(UUID id, Instant endTime) {
}
