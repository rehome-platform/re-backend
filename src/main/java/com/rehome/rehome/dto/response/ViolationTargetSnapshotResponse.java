package com.rehome.rehome.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot của đối tượng bị báo cáo, để Moderator xem trong GET /violation-reports/{id} mà
 * không cần mở riêng màn bài viết/chiến dịch/tổ chức. Chỉ nhóm field khớp targetType mới có giá trị.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationTargetSnapshotResponse {
    private String targetType;
    private String targetId;

    // Tổ chức sở hữu target (chủ bài/chiến dịch, hoặc chính là target khi targetType = ORGANIZATION_ACCOUNT)
    private String organizationId;
    private String organizationDisplayName;

    // targetType = POST
    private String postContent;
    private List<String> postPhotoUrls;
    private String postStatus;

    // targetType = CAMPAIGN
    private String campaignTitle;
    private String campaignDescription;
    private String campaignCoverUrl;
    private String campaignStatus;

    // targetType = ORGANIZATION_ACCOUNT
    private String organizationLegalName;
    private String organizationLogoUrl;
    private String organizationDescription;
    private String organizationVerificationStatus;
}
