package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateLegalChangeRequestRequest;
import com.rehome.rehome.dto.request.RejectLegalChangeRequest;
import com.rehome.rehome.dto.request.RejectOrganizationRequest;
import com.rehome.rehome.dto.request.UpdateOrganizationProfileRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.LegalChangeRequestDetailResponse;
import com.rehome.rehome.dto.response.LegalChangeRequestResponse;
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

    OrganizationPublicProfileResponse getPublicProfile(UUID organizationId, Account viewer);

    void follow(UUID memberId, UUID organizationId);
    void unfollow(UUID memberId, UUID organizationId);

    List<OrganizationNearbyResponse> findNearby(double lat, double lng);

    PageResponse<OrganizationPublicProfileResponse> listFollowing(UUID memberId, int page, int size);

    PageResponse<OrganizationPublicProfileResponse> listPublicOrganizations(String search, UUID viewerId,
                                                                             int page, int size);

    OrganizationProfileUpdateResponse updateMyProfile(UUID accountId, UpdateOrganizationProfileRequest request);

    LegalChangeRequestResponse requestLegalChange(UUID accountId, CreateLegalChangeRequestRequest request);

    LegalChangeRequestDetailResponse getLatestLegalChangeRequest(UUID accountId);

    OrganizationVerificationResponse getVerificationDetail(UUID organizationId);

    OrganizationVerifyResponse verifyOrganization(Account moderator, UUID organizationId);

    OrganizationRejectResponse rejectOrganization(Account moderator, UUID organizationId,
                                                   RejectOrganizationRequest request);

    LegalChangeRequestResponse approveLegalChange(Account moderator, UUID requestId);

    LegalChangeRequestResponse rejectLegalChange(Account moderator, UUID requestId,
                                                 RejectLegalChangeRequest request);

    LegalChangeRequestDetailResponse getLegalChangeRequestDetail(UUID requestId);
}
