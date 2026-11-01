package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateLegalChangeRequestRequest;
import com.rehome.rehome.dto.request.RegisterRequest;
import com.rehome.rehome.dto.request.UpdateOrganizationProfileRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.CampaignService;
import com.rehome.rehome.service.CommunityPostService;
import com.rehome.rehome.service.OrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import com.rehome.rehome.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationProfileController {
    private final OrganizationService organizationService;
    private final AuthService authService;
    private final CampaignService campaignService;
    private final CommunityPostService communityPostService;

    @PostMapping("/register/organization")
    @Operation(summary = "Đăng ký tài khoản tổ chức")
    public ResponseEntity<ApiResponse<RegisterOrganizationResponse>> registerOrganization(
            @Valid @RequestBody RegisterOrganizationRequest request,
            @AuthenticationPrincipal Account account) {
        return ResponseEntity.status(201)
                .body(ApiResponse.success(organizationService.registerOrganization(account.getId(), request), 201));
    }

    @PutMapping("/me/registration")   // class có @RequestMapping("/organizations")
    @Operation(summary = "Tổ chức nộp lại hồ sơ đăng ký sau khi bị từ chối")
    public ResponseEntity<ApiResponse<OrganizationResubmitResponse>> resubmit(
            @AuthenticationPrincipal Account account,
            @Valid @RequestBody RegisterOrganizationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                organizationService.resubmit(account, request),
                "Đã nộp lại hồ sơ, chờ duyệt"));
    }

    @PostMapping("/register/account")
    @Operation(summary = "Đăng ký tài khoản organization")
    public ResponseEntity<ApiResponse<OtpResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(authService.registerOrganization(request), 201));
    }

    @GetMapping("/options")
    @Operation(summary = "Danh sách loại tổ chức và phạm vi hoạt động cho form đăng ký")
    public ResponseEntity<ApiResponse<OrganizationOptionsResponse>> getOptions() {
        return ResponseEntity.ok(ApiResponse.success(organizationService.getOptions()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ORGANIZATION')")
    public ResponseEntity<ApiResponse<OrganizationProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Account account
    ) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.getMyProfile(account.getId())));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('ORGANIZATION')")
    @Operation(summary = "Tổ chức cập nhật thông tin thường, áp dụng ngay không cần duyệt (F-096)")
    public ResponseEntity<ApiResponse<OrganizationProfileUpdateResponse>> updateMyProfile(
            @AuthenticationPrincipal Account account,
            @RequestBody UpdateOrganizationProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                organizationService.updateMyProfile(account.getId(), request), "Đã cập nhật hồ sơ"));
    }

    @PostMapping("/me/legal-change-requests")
    @PreAuthorize("hasRole('ORGANIZATION')")
    @Operation(summary = "Xin đổi thông tin pháp lý, chờ Moderator duyệt mới áp dụng (F-097)")
    public ResponseEntity<ApiResponse<LegalChangeRequestResponse>> requestLegalChange(
            @AuthenticationPrincipal Account account,
            @RequestBody CreateLegalChangeRequestRequest request) {
        LegalChangeRequestResponse result = organizationService.requestLegalChange(account.getId(), request);
        return ResponseEntity.status(201)
                .body(ApiResponse.success(result, 201, "Đã gởi yêu cầu, chờ duyệt"));
    }

    @GetMapping("/me/campaigns")
    @PreAuthorize("hasRole('ORGANIZATION')")
    @Operation(summary = "Danh sách chiến dịch của chính tổ chức, mọi trạng thái (F-103)")
    public ResponseEntity<ApiResponse<PageResponse<CampaignSummaryResponse>>> listMyCampaigns(
            @AuthenticationPrincipal Account account,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                campaignService.listMyCampaigns(account.getId(), status, page, size)));
    }

    @GetMapping("/me/posts")
    @PreAuthorize("hasRole('ORGANIZATION')")
    @Operation(summary = "Bài viết của chính tổ chức kèm số like và bình luận (F-107)")
    public ResponseEntity<ApiResponse<PageResponse<MyPostResponse>>> listMyPosts(
            @AuthenticationPrincipal Account account,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                communityPostService.listMyPosts(account.getId(), page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem hồ sơ công khai của 1 tổ chức đã VERIFIED (F-082)")
    public ResponseEntity<ApiResponse<OrganizationPublicProfileResponse>> getPublicProfile(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {
        OrganizationPublicProfileResponse result = organizationService.getPublicProfile(
                id, account != null ? account.getId() : null);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/follow")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Theo dõi 1 tổ chức (F-085)")
    public ResponseEntity<ApiResponse<Void>> follow(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {
        organizationService.follow(account.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, 204));
    }

    @DeleteMapping("/{id}/follow")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Bỏ theo dõi 1 tổ chức (F-085)")
    public ResponseEntity<ApiResponse<Void>> unfollow(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {
        organizationService.unfollow(account.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, 204));
    }
}
