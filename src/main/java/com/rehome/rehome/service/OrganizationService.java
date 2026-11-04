package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateLegalChangeRequestRequest;
import com.rehome.rehome.dto.request.RejectLegalChangeRequest;
import com.rehome.rehome.dto.request.RejectOrganizationRequest;
import com.rehome.rehome.dto.request.UpdateOrganizationProfileRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.LegalChangeRequestDetailResponse;
import com.rehome.rehome.dto.response.LegalChangeRequestResponse;
import com.rehome.rehome.dto.response.MemberCodeResponse;
import com.rehome.rehome.dto.response.OrganizationNearbyResponse;
import com.rehome.rehome.dto.response.OrganizationOptionsResponse;
import com.rehome.rehome.dto.response.OrganizationProfileResponse;
import com.rehome.rehome.dto.response.OrganizationProfileUpdateResponse;
import com.rehome.rehome.dto.response.OrganizationRejectResponse;
import com.rehome.rehome.dto.response.OrganizationPublicProfileResponse;
import com.rehome.rehome.dto.response.OrganizationResubmitResponse;
import com.rehome.rehome.dto.response.OrganizationVerificationResponse;
import com.rehome.rehome.dto.response.OrganizationVerifyResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.List;
import java.util.UUID;

public interface OrganizationService {
    RegisterOrganizationResponse registerOrganization(UUID accountId, RegisterOrganizationRequest request);
    OrganizationResubmitResponse resubmit(Account account, RegisterOrganizationRequest request);
    OrganizationProfileResponse getMyProfile(UUID accountId);
    OrganizationOptionsResponse getOptions();

    // F-082: trang công khai của 1 tổ chức đã VERIFIED. viewerId == null khi Guest (không có token).
    OrganizationPublicProfileResponse getPublicProfile(UUID organizationId, UUID viewerId);

    // F-085: theo dõi / bỏ theo dõi 1 tổ chức. Cả 2 đều idempotent (gọi lại không báo lỗi).
    void follow(UUID memberId, UUID organizationId);
    void unfollow(UUID memberId, UUID organizationId);

    // Tổ chức đã VERIFIED gần vị trí Member, sắp theo khoảng cách tăng dần, tối đa 20 kết quả.
    List<OrganizationNearbyResponse> findNearby(double lat, double lng);

    // Tổ chức mà chính Member đang theo dõi, mới theo dõi gần đây trước.
    PageResponse<OrganizationPublicProfileResponse> listFollowing(UUID memberId, int page, int size);

    // F-096: tổ chức tự cập nhật thông tin thường, áp dụng ngay.
    OrganizationProfileUpdateResponse updateMyProfile(UUID accountId, UpdateOrganizationProfileRequest request);

    // F-097: xin đổi thông tin pháp lý, phải chờ Moderator duyệt mới áp dụng. Chặn (409) nếu
    // đang có 1 yêu cầu PENDING - mỗi lần chỉ được 1 yêu cầu (OP-05).
    LegalChangeRequestResponse requestLegalChange(UUID accountId, CreateLegalChangeRequestRequest request);

    // OP-05 "Trạng thái yêu cầu": yêu cầu đổi thông tin pháp lý gần nhất của chính tổ chức.
    LegalChangeRequestDetailResponse getLatestLegalChangeRequest(UUID accountId);

    // F-137: hồ sơ xác minh + checklist theo orgType cho Moderator.
    OrganizationVerificationResponse getVerificationDetail(UUID organizationId);

    // F-138: Moderator duyệt tổ chức; chặn nếu hồ sơ chưa đủ giấy tờ theo checklist.
    OrganizationVerifyResponse verifyOrganization(Account moderator, UUID organizationId);

    // F-138: Moderator từ chối hồ sơ kèm lý do để tổ chức sửa và nộp lại.
    OrganizationRejectResponse rejectOrganization(Account moderator, UUID organizationId,
                                                   RejectOrganizationRequest request);

    // F-140: Moderator duyệt yêu cầu đổi thông tin pháp lý -> áp thẳng vào hồ sơ.
    LegalChangeRequestResponse approveLegalChange(Account moderator, UUID requestId);

    // F-140: Moderator từ chối yêu cầu đổi thông tin pháp lý kèm lý do; hồ sơ giữ nguyên.
    LegalChangeRequestResponse rejectLegalChange(Account moderator, UUID requestId,
                                                 RejectLegalChangeRequest request);
}
