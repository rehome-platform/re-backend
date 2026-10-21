package com.rehome.rehome.config;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public final class CodeGenerator {

    private static final int MAX_LENGTH = 10;

    private CodeGenerator() {}

    /** "Áo khoác" -> "AK"; "Kho Quận 10" (bỏ tiền tố "kho") -> "Q10" */
    public static String fromName(String name, String... ignoredLeadingWords) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D');   // đ không tách dấu bằng NFD

        List<String> words = new ArrayList<>(
                Arrays.stream(ascii.trim().split("[^A-Za-z0-9]+"))
                        .filter(w -> !w.isEmpty())
                        .toList());

        if (!words.isEmpty()) {
            for (String prefix : ignoredLeadingWords) {
                if (words.get(0).equalsIgnoreCase(prefix) && words.size() > 1) {
                    words.remove(0);
                    break;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.chars().allMatch(Character::isDigit)) sb.append(w);   // giữ nguyên số
            else sb.append(Character.toUpperCase(w.charAt(0)));
        }

        String code = sb.toString();
        if (code.isEmpty()) code = "X";
        return code.length() > MAX_LENGTH ? code.substring(0, MAX_LENGTH) : code;
    }

    /** Nếu mã đã tồn tại thì thêm hậu tố 2, 3, 4... cho tới khi không trùng */
    public static String makeUnique(String base, Predicate<String> exists) {
        String candidate = base;
        int n = 2;
        while (exists.test(candidate)) {
            String suffix = String.valueOf(n++);
            int keep = Math.min(base.length(), MAX_LENGTH - suffix.length());
            candidate = base.substring(0, keep) + suffix;
        }
        return candidate;
    }
}
