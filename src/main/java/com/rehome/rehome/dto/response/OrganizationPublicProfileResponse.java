package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationPublicProfileResponse {
    private String id;
    private String displayName;
    private String description;
    private String logoUrl;
    private String orgType;
    private String operationScope;
    private Instant verifiedAt;
    private long followerCount;

    private String donationPointName;
    private String donationPointAddress;
    private BigDecimal donationPointLatitude;
    private BigDecimal donationPointLongitude;
    private String donationPointReceivingHours;

    @JsonProperty("isFollowing")
    private boolean isFollowing;
}
