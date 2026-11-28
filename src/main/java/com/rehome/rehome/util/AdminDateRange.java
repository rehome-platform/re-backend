package com.rehome.rehome.util;

import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public record AdminDateRange(Instant from, Instant to, LocalDate fromDate, LocalDate toDate) {

    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant BEGINNING = Instant.EPOCH;
    private static final Instant FOREVER = Instant.parse("3000-01-01T00:00:00Z");

    public static AdminDateRange required(String from, String to) {
        return of(parse(from, true), parse(to, true));
    }

    public static AdminDateRange optional(String from, String to) {
        return of(parse(from, false), parse(to, false));
    }

    public static AdminDateRange of(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }
        Instant from = fromDate == null ? BEGINNING : fromDate.atStartOfDay(VN_ZONE).toInstant();
        Instant to = toDate == null ? FOREVER : toDate.plusDays(1).atStartOfDay(VN_ZONE).toInstant();
        return new AdminDateRange(from, to, fromDate, toDate);
    }

    private static LocalDate parse(String text, boolean required) {
        if (text == null || text.isBlank()) {
            if (required) {
                throw new AppException(ErrorCode.INVALID_DATE_RANGE);
            }
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }
    }
}
