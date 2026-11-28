package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonationResponse {
    private String id;
    private String warehouseId;
    private String warehouseName;
    private String campaignId;
    private String campaignTitle;
    private String donorName;

    @JsonProperty("isAnonymous")
    private boolean isAnonymous;

    private String note;
    @Builder.Default
    private List<String> photoUrls = List.of();
    private String status;

    private String source;

    private String recordedBy;

    private String organizationId;
    private String organizationName;
    private String handedOverToOrganizationName;

    private String productId;
    private String productName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private DonationProductResponse product;

    private Instant heldAt;
    private String holdReason;
    private String reassignedFromCampaignTitle;
    private Instant reassignedAt;
    private Instant handedOverAt;
    private Instant createdAt;
}
