package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateDonationRequest;
import com.rehome.rehome.dto.request.HandoverDonationsRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.DonationHandoverResponse;
import com.rehome.rehome.dto.response.DonationResponse;
import com.rehome.rehome.dto.response.MyWarehouseResponse;
import com.rehome.rehome.dto.response.WarehouseNearestResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.DonationStatus;
import com.rehome.rehome.service.DonationService;
import com.rehome.rehome.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
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
import com.rehome.rehome.dto.marketplace.response.WarehouseResponse;
/**
 * API 14 - GET /warehouses/nearest (F-015, Role: Member).
 * Trả về danh sách kho ACTIVE sắp xếp theo khoảng cách tăng dần so với vị trí Member.
 */
@RestController
@RequestMapping("/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final DonationService donationService;

    @GetMapping("/nearest")
    @PreAuthorize("hasRole('MEMBER')")
    public ApiResponse<List<WarehouseNearestResponse>> getNearest(
            @RequestParam double lat,
            @RequestParam double lng) {
        return ApiResponse.success(warehouseService.getNearest(lat, lng));
    }

    @GetMapping
    public ResponseEntity<List<WarehouseResponse>> getWarehouses() {
        return ResponseEntity.ok(warehouseService.getActiveWarehouses());
    }


    @GetMapping("/moderator")
    @PreAuthorize("hasRole('MODERATOR')")
    public List<MyWarehouseResponse> getMyWarehouses(@AuthenticationPrincipal Account account) {
        return warehouseService.getManagedWarehouses(account.getId());
    }

    @PostMapping("/{id}/donations")
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "Ghi nhận 1 lượt quyên góp Member mang đến điểm ký gửi (F-163)")
    public ResponseEntity<ApiResponse<DonationResponse>> recordDonation(
            @PathVariable UUID id,
            @RequestBody CreateDonationRequest request,
            @AuthenticationPrincipal Account account) {
        DonationResponse result = donationService.recordDonation(account, id, request);
        return ResponseEntity.status(201)
                .body(ApiResponse.success(result, 201, "Đã ghi nhận lượt quyên góp"));
    }

    @GetMapping("/{id}/donations")
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "Danh sách lượt quyên góp tại điểm ký gửi, lọc theo chiến dịch và trạng thái (F-164)")
    public ResponseEntity<ApiResponse<List<DonationResponse>>> listDonations(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) DonationStatus status,
            @AuthenticationPrincipal Account account) {
        List<DonationResponse> result = donationService.listDonations(account, id, campaignId, status);
        return ResponseEntity.ok(ApiResponse.success(result, "OK"));
    }

    @PostMapping("/{id}/donations/handover")
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "Xác nhận bàn giao các lượt quyên góp chờ lấy cho tổ chức (F-165)")
    public ResponseEntity<ApiResponse<DonationHandoverResponse>> handoverDonations(
            @PathVariable UUID id,
            @RequestBody HandoverDonationsRequest request,
            @AuthenticationPrincipal Account account) {
        DonationHandoverResponse result = donationService.handoverDonations(account, id, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Đã xác nhận bàn giao cho tổ chức"));
    }
}