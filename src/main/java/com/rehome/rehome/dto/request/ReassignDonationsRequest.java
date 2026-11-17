package com.rehome.rehome.dto.request;

import java.util.List;
import java.util.UUID;

public record ReassignDonationsRequest(List<String> donationIds, UUID campaignId) {
}
