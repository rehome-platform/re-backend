package com.rehome.rehome.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class NotificationFormatting {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private NotificationFormatting() {}

    public static String deadline(LocalDate value) {
        return value == null ? "" : DATE.format(value);
    }

    public static String deadline(Instant value) {
        return value == null ? "" : TIME.format(value);
    }

    public static String vnd(BigDecimal value) {
        if (value == null) return "";
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value) + " đ";
    }
}
