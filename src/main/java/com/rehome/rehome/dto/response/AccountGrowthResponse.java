package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/** GET /api/admin/accounts/growth - số tài khoản đăng ký theo ngày/tuần/tháng (giờ VN). */
@Getter
@Builder
@AllArgsConstructor
public class AccountGrowthResponse {
    // day | week | month
    private final String granularity;
    private final LocalDate from;
    private final LocalDate to;
    // Tổng số đăng ký trong khoảng
    private final long total;
    // Đủ mọi kỳ trong khoảng (kỳ không có đăng ký có count = 0) để vẽ biểu đồ liền mạch
    private final List<Point> points;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Point {
        // Ví dụ 2026-11-11 (day), 2026-W46 (week, tuần ISO bắt đầu thứ Hai), 2026-11 (month)
        private final String label;
        // Kỳ đã cắt theo [from, to]: kỳ tuần/tháng đầu và cuối có thể ngắn hơn một tuần/tháng đủ
        private final LocalDate periodStart;
        private final LocalDate periodEnd;
        private final long count;
    }
}
