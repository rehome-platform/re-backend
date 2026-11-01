package com.rehome.rehome.util;

import java.text.Normalizer;
import java.util.Locale;

// Chuẩn hóa chuỗi tiếng Việt về dạng không dấu + viết thường, để người dùng gõ "ao khoac"
// vẫn tìm ra "Áo khoác".
//
// Không dùng extension unaccent của PostgreSQL: bộ test chạy trên H2 (không có unaccent) nên
// biểu thức tìm kiếm sẽ không test được, và Postgres managed không chắc cho phép
// CREATE EXTENSION. Chuẩn hóa ở tầng Java rồi so trên cột đã chuẩn hóa chạy giống nhau trên cả hai.
public final class VietnameseText {

    private VietnameseText() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }

        // NFD tách nguyên âm khỏi dấu (cả dấu móc của ơ/ư), bỏ dấu đi là còn chữ cái gốc.
        // Riêng đ/Đ là một ký tự độc lập, NFD không tách được nên phải thay tay.
        String withoutDiacritics = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');

        return withoutDiacritics.toLowerCase(Locale.ROOT).trim();
    }
}
