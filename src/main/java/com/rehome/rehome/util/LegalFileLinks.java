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

    // F-003: mobile đọc link bảo vệ từ /me rồi gửi lại nguyên văn khi nộp lại hồ sơ để giữ
    // tệp cũ (không bắt tổ chức upload lại). Các hàm dưới đây nhận diện đúng link đó, để tầng
    // ghi phân biệt được "đây là link server trả, không phải URL storage thật" và giữ tệp cũ.
    public static boolean isCccdFrontLink(UUID organizationId, String value) {
        return value != null && value.equals(cccdFront(organizationId));
    }

    public static boolean isCccdBackLink(UUID organizationId, String value) {
        return value != null && value.equals(cccdBack(organizationId));
    }

    /** documentId trong link nếu value đúng là link bảo vệ của 1 giấy tờ thuộc tổ chức này. */
    public static UUID documentIdFromLinkOrNull(UUID organizationId, String value) {
        if (value == null) {
            return null;
        }
        String prefix = BASE + organizationId + SEGMENT + "documents/";
        if (!value.startsWith(prefix)) {
            return null;
        }
        try {
            return UUID.fromString(value.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
