package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateViolationReportRequest;
import com.rehome.rehome.dto.request.DismissViolationReportRequest;
import com.rehome.rehome.dto.request.ResolveViolationReportRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.ViolationReportDetailResponse;
import com.rehome.rehome.dto.response.ViolationReportGroupResponse;
import com.rehome.rehome.dto.response.ViolationReportResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.ViolationReportService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/violation-reports")
@RequiredArgsConstructor
public class ViolationReportController {

    private final ViolationReportService violationReportService;

    @PostMapping
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Báo cáo vi phạm: bài viết, chiến dịch hoặc tài khoản tổ chức (F-088)")
    public ResponseEntity<ApiResponse<ViolationReportResponse>> create(
            @RequestBody CreateViolationReportRequest request,
            @AuthenticationPrincipal Account account) {
        ViolationReportResponse result = violationReportService.create(account.getId(), request);
        return ResponseEntity.status(201).body(ApiResponse.success(result, 201, "Đã gởi báo cáo"));
    }

    @GetMapping
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "Hàng đợi báo cáo vi phạm, gộp theo target - lọc theo status/type, sắp theo số báo cáo DESC rồi cũ nhất ASC")
    public ResponseEntity<ApiResponse<List<ViolationReportGroupResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(ApiResponse.success(violationReportService.list(status, type)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "Chi tiết 1 báo cáo: snapshot target, các báo cáo khác cùng target, lịch sử vi phạm của tổ chức")
    public ResponseEntity<ApiResponse<ViolationReportDetailResponse>> getDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(violationReportService.getDetail(id)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "Xử lý báo cáo: thực hiện action trên target, đóng mọi báo cáo PENDING cùng target")
    public ResponseEntity<ApiResponse<ViolationReportDetailResponse>> resolve(
            @PathVariable UUID id,
            @RequestBody ResolveViolationReportRequest request,
            @AuthenticationPrincipal Account moderator) {
        ViolationReportDetailResponse result = violationReportService.resolve(moderator, id, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Đã xử lý báo cáo"));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "Bỏ qua báo cáo: đóng mọi báo cáo PENDING cùng target, không đổi gì trên nội dung")
    public ResponseEntity<ApiResponse<ViolationReportDetailResponse>> dismiss(
            @PathVariable UUID id,
            @RequestBody DismissViolationReportRequest request,
            @AuthenticationPrincipal Account moderator) {
        ViolationReportDetailResponse result = violationReportService.dismiss(moderator, id, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Đã bỏ qua báo cáo"));
    }
}
