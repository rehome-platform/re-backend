package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AdminDashboardResponse;

public interface AdminDashboardService {

    // API 147 - F-158: from/to (yyyy-MM-dd, gồm cả ngày to) đều tùy chọn; bỏ trống cả hai = toàn thời gian
    AdminDashboardResponse getDashboard(String from, String to);

    // API 148 - F-159: file .xlsx cho khoảng [from, to] (yyyy-MM-dd, gồm cả ngày to). `sections` là danh
    // sách khối cần xuất cách nhau dấu phẩy (REVENUE, USERS, WAREHOUSE - không phân biệt hoa thường); bỏ
    // trống/null thì xuất cả ba, giữ đúng hành vi cũ. Bảng "Món đã bán" luôn có trong file, không phụ
    // thuộc `sections`.
    DashboardExport export(String from, String to, String sections);

    record DashboardExport(String fileName, byte[] content) {}

    /** Admin tick chọn khối nào thì xuất khối đó; bỏ trống "sections" ở request = cả ba (xem ExportSections.parse). */
    record ExportSections(boolean revenue, boolean users, boolean warehouse) {
        public static ExportSections parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new ExportSections(true, true, true);
            }
            java.util.Set<String> keys = java.util.Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toUpperCase(java.util.Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());
            return new ExportSections(keys.contains("REVENUE"), keys.contains("USERS"), keys.contains("WAREHOUSE"));
        }
    }

    /** Số liệu khối "Người dùng" cho file Excel - đếm hiện tại theo vai trò + tài khoản mới trong khoảng xuất. */
    record UsersExportData(long memberCount, long organizationCount, long moderatorCount, long newAccountsCount) {}
}
