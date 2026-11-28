package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AdminMembershipPaymentResponse;
import com.rehome.rehome.dto.response.PageResponse;

public interface MembershipPaymentAdminService {

    PageResponse<AdminMembershipPaymentResponse> search(
            String from, String to, String tier, String method, int page, int size);
}
