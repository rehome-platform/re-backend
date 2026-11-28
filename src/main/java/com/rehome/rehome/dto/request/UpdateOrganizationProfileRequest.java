package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrganizationProfileRequest {
    private String displayName;
    private String logoUrl;
    private String description;
    private String contactPhone;
    private String donationPointReceivingHours;
}
