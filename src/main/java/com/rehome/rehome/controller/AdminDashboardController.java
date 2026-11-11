package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.AdminDashboardResponse;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Dashboard")
public class AdminDashboardController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final AdminDashboardService adminDashboardService;

    // from/to tùy chọn (yyyy-MM-dd, gồm cả ngày to). Ngày/tuần/tháng do client chọn khoảng tương ứng.
    // Doanh thu, số đơn, điểm đánh giá, cơ cấu nguồn thu lọc theo khoảng; tồn kho, số dư ví, quỹ,
    // việc quá hạn luôn là giá trị hiện tại.
    @GetMapping
    @Operation(summary = "API 147 - Dashboard tổng quan vận hành (F-158), lọc tùy chọn theo from/to yyyy-MM-dd")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> dashboard(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getDashboard(from, to), "OK"));
    }

    // from/to nhận String để sai định dạng cũng ra 400 INVALID_DATE_RANGE đúng message trong docs
    @GetMapping("/export")
    @Operation(summary = "API 148 - Xuất báo cáo dashboard ra Excel (F-159), from/to dạng yyyy-MM-dd")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        AdminDashboardService.DashboardExport file = adminDashboardService.export(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .body(file.content());
    }
}
