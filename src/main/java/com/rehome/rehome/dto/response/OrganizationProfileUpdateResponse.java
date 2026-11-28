package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationProfileUpdateResponse {
    private String id;
    private String displayName;
    private String logoUrl;
    private String description;
    private String contactPhone;
    private String donationPointReceivingHours;
}
