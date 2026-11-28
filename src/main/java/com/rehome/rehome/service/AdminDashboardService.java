package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AdminDashboardResponse;

public interface AdminDashboardService {

    AdminDashboardResponse getDashboard(String from, String to);

    DashboardExport export(String from, String to, String sections);

    record DashboardExport(String fileName, byte[] content) {}

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

    record UsersExportData(long memberCount, long organizationCount, long moderatorCount, long newAccountsCount) {}
}
