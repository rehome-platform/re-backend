package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.RevenueTrendResponse;
import com.rehome.rehome.service.RevenueTrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard/revenue-trend")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Dashboard")
public class RevenueTrendAdminController {

    private final RevenueTrendService revenueTrendService;

    @GetMapping
    @Operation(summary = "Doanh thu nền tảng theo ngày/tuần/tháng (granularity=day|week|month), from/to yyyy-MM-dd; bỏ from = từ ngày có doanh thu đầu tiên")
    public ResponseEntity<ApiResponse<RevenueTrendResponse>> trend(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "day") String granularity) {
        return ResponseEntity.ok(ApiResponse.success(revenueTrendService.trend(from, to, granularity)));
    }
}
