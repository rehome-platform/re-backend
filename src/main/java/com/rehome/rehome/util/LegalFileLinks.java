package com.rehome.rehome.util;

import java.util.UUID;

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

    public static String cccdFrontOrNull(UUID organizationId, String storedUrl) {
        return storedUrl == null || storedUrl.isBlank() ? null : cccdFront(organizationId);
    }

    public static String cccdBackOrNull(UUID organizationId, String storedUrl) {
        return storedUrl == null || storedUrl.isBlank() ? null : cccdBack(organizationId);
    }

    public static String documentOrNull(UUID organizationId, UUID documentId, String storedUrl) {
        return storedUrl == null || storedUrl.isBlank() ? null : document(organizationId, documentId);
    }

    public static boolean isCccdFrontLink(UUID organizationId, String value) {
        return value != null && value.equals(cccdFront(organizationId));
    }

    public static boolean isCccdBackLink(UUID organizationId, String value) {
        return value != null && value.equals(cccdBack(organizationId));
    }

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
