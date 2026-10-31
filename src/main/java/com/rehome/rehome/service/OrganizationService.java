package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateLegalChangeRequestRequest;
import com.rehome.rehome.dto.request.UpdateOrganizationProfileRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.LegalChangeRequestResponse;
import com.rehome.rehome.dto.response.MemberCodeResponse;
import com.rehome.rehome.dto.response.OrganizationOptionsResponse;
import com.rehome.rehome.dto.response.OrganizationProfileResponse;
import com.rehome.rehome.dto.response.OrganizationProfileUpdateResponse;
import com.rehome.rehome.dto.response.OrganizationPublicProfileResponse;
import com.rehome.rehome.dto.response.OrganizationResubmitResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;

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

    // F-096: tổ chức tự cập nhật thông tin thường, áp dụng ngay.
    OrganizationProfileUpdateResponse updateMyProfile(UUID accountId, UpdateOrganizationProfileRequest request);

    // F-097: xin đổi thông tin pháp lý, phải chờ Moderator duyệt mới áp dụng.
    LegalChangeRequestResponse requestLegalChange(UUID accountId, CreateLegalChangeRequestRequest request);
}
