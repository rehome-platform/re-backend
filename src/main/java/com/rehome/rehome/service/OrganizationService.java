package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.OrganizationResubmitResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;

public interface OrganizationService {
    RegisterOrganizationResponse registerOrganization(Account account, RegisterOrganizationRequest request);
    OrganizationResubmitResponse resubmit(Account account, RegisterOrganizationRequest request);
}
