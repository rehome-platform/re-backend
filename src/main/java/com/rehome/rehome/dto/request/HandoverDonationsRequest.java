package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandoverDonationsRequest {
    private List<String> donationIds;
    private String organizationId;
    private String campaignId;
}
