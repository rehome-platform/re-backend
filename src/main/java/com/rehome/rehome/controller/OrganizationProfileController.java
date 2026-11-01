package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateDonationNoteRequest;
import com.rehome.rehome.dto.request.CreateLegalChangeRequestRequest;
import com.rehome.rehome.dto.request.RegisterRequest;
import com.rehome.rehome.dto.request.RejectOrganizationRequest;
import com.rehome.rehome.dto.request.UpdateOrganizationProfileRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.DonationStatus;
import com.rehome.rehome.entity.enums.LegalFileKind;
import com.rehome.rehome.service.CampaignService;
import com.rehome.rehome.service.CommunityPostService;
import com.rehome.rehome.service.DonationService;
import com.rehome.rehome.service.OrganizationLegalFileService;
import com.rehome.rehome.service.OrganizationService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import com.rehome.rehome.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationProfileController {
    private final OrganizationService organizationService;
    private final AuthService authService;
    private final CampaignService campaignService;
    private final CommunityPostService communityPostService;
    private final DonationService donationService;
    private final OrganizationLegalFileService organizationLegalFileService;

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

    @GetMapping("/me/donations")
    @PreAuthorize("hasRole('ORGANIZATION')")
    @Operation(summary = "Lượt quyên góp đang chờ lấy và lượt tổ chức đã nhận (F-166)")
    public ResponseEntity<ApiResponse<List<DonationResponse>>> listMyDonations(
            @AuthenticationPrincipal Account account,
            @RequestParam(required = false) DonationStatus status) {
        List<DonationResponse> result =
                donationService.listOrganizationDonations(account.getId(), status);
        return ResponseEntity.ok(ApiResponse.success(result, "OK"));
    }

    @PostMapping("/me/donation-notes")
    @PreAuthorize("hasRole('ORGANIZATION')")
    @Operation(summary = "Ghi note khi nhận quyên góp trực tiếp tại địa chỉ riêng (F-167)")
    public ResponseEntity<ApiResponse<DonationNoteResponse>> createDonationNote(
            @AuthenticationPrincipal Account account,
            @RequestBody CreateDonationNoteRequest request) {
        DonationNoteResponse result = donationService.createDonationNote(account.getId(), request);
        return ResponseEntity.status(201)
                .body(ApiResponse.success(result, 201, "Đã ghi nhận note quyên góp"));
    }

    @GetMapping("/{id}/verification")
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "Hồ sơ xác minh + checklist theo loại tổ chức, chỉ Moderator xem (F-137)")
    public ResponseEntity<ApiResponse<OrganizationVerificationResponse>> getVerificationDetail(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.getVerificationDetail(id)));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "Moderator xác minh và duyệt tổ chức (F-138)")
    public ResponseEntity<ApiResponse<OrganizationVerifyResponse>> verifyOrganization(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {
        OrganizationVerifyResponse result = organizationService.verifyOrganization(account, id);
        return ResponseEntity.ok(ApiResponse.success(result, "Đã xác minh tổ chức"));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "Moderator từ chối hồ sơ tổ chức kèm lý do (F-138)")
    public ResponseEntity<ApiResponse<OrganizationRejectResponse>> rejectOrganization(
            @PathVariable UUID id,
            @RequestBody RejectOrganizationRequest request,
            @AuthenticationPrincipal Account account) {
        OrganizationRejectResponse result = organizationService.rejectOrganization(account, id, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Đã từ chối hồ sơ"));
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

    // ------------------------------------------------------------------
    // F-139: ảnh giấy tờ pháp lý chỉ ra qua 3 endpoint dưới đây. API không trả URL storage
    // nữa, server tự đọc byte rồi stream để kiểm quyền được từng request.
    // ------------------------------------------------------------------

    @GetMapping("/{id}/legal-files/cccd-front")
    @PreAuthorize("hasAnyRole('MODERATOR','ORGANIZATION')")
    @Operation(summary = "Xem ảnh CCCD mặt trước của người đại diện (F-139)")
    public ResponseEntity<byte[]> getCccdFront(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id) {
        return streamLegalFile(account, id, LegalFileKind.CCCD_FRONT, null);
    }

    @GetMapping("/{id}/legal-files/cccd-back")
    @PreAuthorize("hasAnyRole('MODERATOR','ORGANIZATION')")
    @Operation(summary = "Xem ảnh CCCD mặt sau của người đại diện (F-139)")
    public ResponseEntity<byte[]> getCccdBack(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id) {
        return streamLegalFile(account, id, LegalFileKind.CCCD_BACK, null);
    }

    @GetMapping("/{id}/legal-files/documents/{documentId}")
    @PreAuthorize("hasAnyRole('MODERATOR','ORGANIZATION')")
    @Operation(summary = "Xem ảnh hoặc PDF một giấy tờ pháp lý của tổ chức (F-139)")
    public ResponseEntity<byte[]> getLegalDocument(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id,
            @PathVariable UUID documentId) {
        return streamLegalFile(account, id, LegalFileKind.DOCUMENT, documentId);
    }

    private ResponseEntity<byte[]> streamLegalFile(Account account, UUID organizationId,
                                                   LegalFileKind kind, UUID documentId) {
        LegalFileDownload download =
                organizationLegalFileService.download(account, organizationId, kind, documentId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(download.fileName()).build().toString())
                // Ảnh giấy tờ không được nằm lại trong cache của proxy hay trình duyệt
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentLength(download.content().length)
                .body(download.content());
    }
}
