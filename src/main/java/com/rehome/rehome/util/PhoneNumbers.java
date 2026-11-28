package com.rehome.rehome.util;

import java.util.regex.Pattern;

public final class PhoneNumbers {

    private static final Pattern VALID = Pattern.compile("^0\\d{9}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()]");
    private static final Pattern PHONE_LIKE = Pattern.compile("^\\+?[\\d\\s.\\-()]+$");

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String phone = SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (phone.isEmpty()) {
            return null;
        }
        if (phone.startsWith("+84")) {
            return "0" + phone.substring(3);
        }
        if (phone.startsWith("84") && phone.length() == 11) {
            return "0" + phone.substring(2);
        }
        return phone;
    }

    public static boolean isValid(String normalized) {
        return normalized != null && VALID.matcher(normalized).matches();
    }

    public static String toSearchKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String trimmed = keyword.trim();
        if (!trimmed.isEmpty() && PHONE_LIKE.matcher(trimmed).matches()
                && trimmed.chars().anyMatch(Character::isDigit)) {
            String stripped = SEPARATORS.matcher(trimmed).replaceAll("");
            return stripped.startsWith("+84") ? "0" + stripped.substring(3) : stripped;
        }
        return trimmed;
    }
}
