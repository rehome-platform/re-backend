package com.rehome.rehome.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UploadFolder {

    PRODUCTS("products", false),

    DONATION("donations", false),

    CONSIGNMENT_FORMS("consignment-forms", false),

    RETURN_REQUESTS("return-requests", false),

    COMMUNITY_POSTS("community-posts", false),

    TRY_ON("try-on", false),

    AVATARS("avatars", false),

    ORGANIZATION_LOGOS("organization-logos", false),

    CAMPAIGN_COVERS("campaign-covers", false),

    ORGANIZATION_DOCUMENTS("organization-documents", true),

    CHAT_IMAGES("chat-images", false),

    ORDER_RATINGS("order-ratings", false),
    ORDER_PACKING("order-packing", false);

    private final String path;

    private final boolean pdfAllowed;
}
