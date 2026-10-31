package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateCampaignRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CampaignPublicResponse;
import com.rehome.rehome.dto.response.CampaignStatusResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.CampaignService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết công khai 1 chiến dịch (F-083)")
    public ResponseEntity<ApiResponse<CampaignPublicResponse>> getPublicDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.getPublicDetail(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZATION')")
    @Operation(summary = "Tổ chức tạo chiến dịch mới, chặn nếu đã đạt hạn mức đang chạy (F-098/F-099)")
    public ResponseEntity<ApiResponse<CampaignStatusResponse>> createCampaign(
            @RequestBody CreateCampaignRequest request,
            @AuthenticationPrincipal Account account) {
        CampaignStatusResponse result = campaignService.createCampaign(account.getId(), request);
        return ResponseEntity.status(201)
                .body(ApiResponse.success(result, 201, "Đã tạo chiến dịch"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZATION')")
    @Operation(summary = "Sửa chiến dịch, chỉ khi đang mở (F-100)")
    public ResponseEntity<ApiResponse<CampaignPublicResponse>> updateCampaign(
            @PathVariable UUID id,
            @RequestBody CreateCampaignRequest request,
            @AuthenticationPrincipal Account account) {
        CampaignPublicResponse result = campaignService.updateCampaign(account.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Đã cập nhật chiến dịch"));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ORGANIZATION')")
    @Operation(summary = "Tổ chức đóng chiến dịch trước hạn (F-101)")
    public ResponseEntity<ApiResponse<CampaignStatusResponse>> closeCampaign(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {
        CampaignStatusResponse result = campaignService.closeCampaign(account.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(result, "Đã đóng chiến dịch"));
    }
}
