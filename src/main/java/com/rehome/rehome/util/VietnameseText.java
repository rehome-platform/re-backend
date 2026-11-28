package com.rehome.rehome.util;

import java.text.Normalizer;
import java.util.Locale;

public final class VietnameseText {

    private VietnameseText() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String withoutDiacritics = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');

        return withoutDiacritics.toLowerCase(Locale.ROOT).trim();
    }
}
