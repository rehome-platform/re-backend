package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.MemberCodeResponse;
import com.rehome.rehome.dto.response.OrganizationOptionsResponse;
import com.rehome.rehome.dto.response.OrganizationProfileResponse;
import com.rehome.rehome.dto.response.OrganizationResubmitResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface OrganizationService {
    RegisterOrganizationResponse registerOrganization(UUID accountId, RegisterOrganizationRequest request);
    OrganizationResubmitResponse resubmit(Account account, RegisterOrganizationRequest request);
    OrganizationProfileResponse getMyProfile(UUID accountId);
    OrganizationOptionsResponse getOptions();
}
