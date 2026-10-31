package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateViolationReportRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.ViolationReportResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.ViolationReportService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
