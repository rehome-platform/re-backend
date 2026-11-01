package com.rehome.rehome.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Các thư mục được phép upload. Client chỉ gửi tên enum, không gửi đường dẫn tự do,
 * để không tự đặt được path tùy ý trên storage.
 */
@Getter
@RequiredArgsConstructor
public enum UploadFolder {

    /** Ảnh sản phẩm - {@link com.rehome.rehome.entity.marketplace.ProductPhoto} */
    PRODUCTS("products", false),

    /** Ảnh Member chụp kèm khi tạo form ký gửi - ConsignmentFormPhoto */
    CONSIGNMENT_FORMS("consignment-forms", false),

    /** Ảnh bằng chứng khi yêu cầu trả hàng - ReturnRequestPhoto */
    RETURN_REQUESTS("return-requests", false),

    /** Ảnh bài viết cộng đồng - CommunityPostPhoto */
    COMMUNITY_POSTS("community-posts", false),

    /** Ảnh người dùng upload để thử đồ ảo - TryOnImage */
    TRY_ON("try-on", false),

    /** Ảnh đại diện - MemberProfile.avatarUrl */
    AVATARS("avatars", false),

    /** Logo tổ chức - OrganizationProfile.logoUrl */
    ORGANIZATION_LOGOS("organization-logos", false),

    /** Ảnh bìa chiến dịch - Campaign.coverUrl (OC-02) */
    CAMPAIGN_COVERS("campaign-covers", false),

    /** Giấy tờ pháp lý của tổ chức - OrganizationDocument.fileUrl (ảnh scan hoặc PDF) */
    ORGANIZATION_DOCUMENTS("organization-documents", true);

    /** Đường dẫn thật trên storage */
    private final String path;

    /** true = chấp nhận cả PDF, false = chỉ ảnh */
    private final boolean pdfAllowed;
}
