package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/** GET /api/admin/dashboard/revenue-trend - doanh thu nền tảng (hoa hồng + phí rút hàng) theo ngày/tuần/tháng (giờ VN). */
@Getter
@Builder
@AllArgsConstructor
public class RevenueTrendResponse {
    // day | week | month
    private final String granularity;
    private final LocalDate from;
    private final LocalDate to;
    // Tổng doanh thu nền tảng trong khoảng (đồng), bằng platformRevenue của API 147 cùng khoảng
    private final long total;
    // Đủ mọi kỳ trong khoảng (kỳ không có doanh thu có amount = 0) để vẽ biểu đồ liền mạch
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
        // Doanh thu nền tảng của kỳ, đồng
        private final long amount;
    }
}
