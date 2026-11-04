package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AdminDashboardResponse;

public interface AdminDashboardService {

    // API 147 - F-158: số liệu toàn thời gian
    AdminDashboardResponse getDashboard();

    // API 148 - F-159: file .xlsx cho khoảng [from, to] (yyyy-MM-dd, gồm cả ngày to)
    DashboardExport export(String from, String to);

    record DashboardExport(String fileName, byte[] content) {}
}
