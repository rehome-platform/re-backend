package com.rehome.rehome.dto.response;

import java.time.Instant;
import java.util.UUID;

public record OrganizationNearbyCampaignResponse(UUID id, Instant endTime) {
}
