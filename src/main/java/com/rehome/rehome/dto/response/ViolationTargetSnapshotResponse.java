package com.rehome.rehome.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationTargetSnapshotResponse {
    private String targetType;
    private String targetId;

    private String organizationId;
    private String organizationDisplayName;

    private String postContent;
    private List<String> postPhotoUrls;
    private String postStatus;

    private String campaignTitle;
    private String campaignDescription;
    private String campaignCoverUrl;
    private String campaignStatus;

    private String organizationLegalName;
    private String organizationLogoUrl;
    private String organizationDescription;
    private String organizationVerificationStatus;
}
