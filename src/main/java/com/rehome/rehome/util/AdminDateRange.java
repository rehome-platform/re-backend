package com.rehome.rehome.util;

import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Khoảng ngày yyyy-MM-dd (giờ VN, gồm cả ngày "to") đổi sang khoảng Instant nửa mở [from, to).
 * Dùng chung cho các API báo cáo của Admin. from/to nhận String để sai định dạng cũng ra
 * 400 INVALID_DATE_RANGE thay vì lỗi ép kiểu mặc định của Spring.
 * Cận không bị chặn luôn là Instant thật (không null): Postgres không suy được kiểu của tham số null
 * trong "(:p is null or ...)".
 */
public record AdminDateRange(Instant from, Instant to, LocalDate fromDate, LocalDate toDate) {

    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant BEGINNING = Instant.EPOCH;
    private static final Instant FOREVER = Instant.parse("3000-01-01T00:00:00Z");

    /** Cả from và to đều bắt buộc. */
    public static AdminDateRange required(String from, String to) {
        return of(parse(from, true), parse(to, true));
    }

    /** from/to có thể bỏ trống: bỏ from = từ đầu, bỏ to = đến hết. */
    public static AdminDateRange optional(String from, String to) {
        return of(parse(from, false), parse(to, false));
    }

    public static AdminDateRange of(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }
        Instant from = fromDate == null ? BEGINNING : fromDate.atStartOfDay(VN_ZONE).toInstant();
        // Gồm cả ngày "to": cận trên là 00:00 ngày kế tiếp (giờ VN)
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
