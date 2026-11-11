package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.RevenueItemResponse;
import com.rehome.rehome.service.AdminRevenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** đã được SecurityConfig giới hạn cho ADMIN; @PreAuthorize là lớp bảo vệ thứ hai
@RestController
@RequestMapping("/api/admin/revenue")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Revenue")
public class AdminRevenueController {

    private final AdminRevenueService adminRevenueService;

    // from/to nhận String để sai định dạng cũng ra 400 INVALID_DATE_RANGE
    @GetMapping("/items")
    @Operation(summary = "Danh sách món đã bán theo kỳ: lọc ngày hoàn thành đơn (from/to yyyy-MM-dd) và kỳ ký gửi (term), phân trang")
    public ResponseEntity<ApiResponse<PageResponse<RevenueItemResponse>>> soldItems(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer term,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminRevenueService.listSoldItems(from, to, term, page, size)));
    }
}
