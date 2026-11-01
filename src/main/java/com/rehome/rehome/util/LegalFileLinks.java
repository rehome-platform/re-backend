package com.rehome.rehome.util;

import java.util.UUID;

/**
 * Đường dẫn endpoint có kiểm quyền để xem ảnh giấy tờ pháp lý của tổ chức (F-139).
 *
 * <p>DB vẫn lưu URL công khai của storage, nhưng URL đó không được trả ra API nữa:
 * mọi response (API 128, hồ sơ /me) chỉ trả các đường dẫn dưới đây, và chúng đòi
 * header Authorization của Moderator hoặc của chính tổ chức.
 */
public final class LegalFileLinks {

    private static final String BASE = "/api/organizations/";
    private static final String SEGMENT = "/legal-files/";

    private LegalFileLinks() {
    }

    public static String cccdFront(UUID organizationId) {
        return BASE + organizationId + SEGMENT + "cccd-front";
    }

    public static String cccdBack(UUID organizationId) {
        return BASE + organizationId + SEGMENT + "cccd-back";
    }

    public static String document(UUID organizationId, UUID documentId) {
        return BASE + organizationId + SEGMENT + "documents/" + documentId;
    }

    /** Chỉ sinh link khi hồ sơ thật sự còn tệp — ảnh hết hạn giữ đã bị job F-139 xóa. */
    public static String cccdFrontOrNull(UUID organizationId, String storedUrl) {
        return storedUrl == null || storedUrl.isBlank() ? null : cccdFront(organizationId);
    }

    public static String cccdBackOrNull(UUID organizationId, String storedUrl) {
        return storedUrl == null || storedUrl.isBlank() ? null : cccdBack(organizationId);
    }

    public static String documentOrNull(UUID organizationId, UUID documentId, String storedUrl) {
        return storedUrl == null || storedUrl.isBlank() ? null : document(organizationId, documentId);
    }
}
