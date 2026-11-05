package com.rehome.rehome.util;

import java.util.regex.Pattern;

/**
 * Chuẩn hóa và kiểm tra số điện thoại Việt Nam dạng 0xxxxxxxxx (10 số), cùng định dạng
 * với MemberServiceImpl. Chấp nhận thêm +84 / 84 ở đầu và các dấu phân cách khi nhập.
 */
public final class PhoneNumbers {

    private static final Pattern VALID = Pattern.compile("^0\\d{9}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()]");
    // Từ khoá tìm kiếm "trông như số điện thoại": chỉ gồm chữ số, +, khoảng trắng, . - ( )
    private static final Pattern PHONE_LIKE = Pattern.compile("^\\+?[\\d\\s.\\-()]+$");

    private PhoneNumbers() {
    }

    /** Bỏ dấu phân cách, đổi +84 / 84 thành 0. Trả null nếu rỗng. Không kiểm tra hợp lệ. */
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
        // 84 + 9 số = 11 số: tránh nhầm với số nội địa bắt đầu bằng 0
        if (phone.startsWith("84") && phone.length() == 11) {
            return "0" + phone.substring(2);
        }
        return phone;
    }

    /** Hợp lệ khi đã chuẩn hóa và đúng dạng 0 + 9 chữ số. */
    public static boolean isValid(String normalized) {
        return normalized != null && VALID.matcher(normalized).matches();
    }

    /**
     * Dùng cho ô tìm kiếm: nếu từ khoá trông như số điện thoại thì bỏ dấu phân cách và đổi +84 thành 0
     * để khớp với dữ liệu lưu trong DB; ngược lại giữ nguyên (tìm theo email/tên).
     */
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
