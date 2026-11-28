package com.rehome.rehome.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class VnTimeWindow {

    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private VnTimeWindow() {}

    public record Window(Instant from, Instant to) {}

    public static Window today() {
        LocalDate today = LocalDate.now(VN_ZONE);
        return new Window(
                today.atStartOfDay(VN_ZONE).toInstant(),
                today.plusDays(1).atStartOfDay(VN_ZONE).toInstant());
    }

    public static Window thisMonth() {
        LocalDate first = LocalDate.now(VN_ZONE).withDayOfMonth(1);
        return new Window(
                first.atStartOfDay(VN_ZONE).toInstant(),
                first.plusMonths(1).atStartOfDay(VN_ZONE).toInstant());
    }
}
