package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.UpdateMemberProfileRequest;
import com.rehome.rehome.dto.response.MemberCodeResponse;
import com.rehome.rehome.dto.response.MemberProfileResponse;
import com.rehome.rehome.dto.response.MemberProfileUpdateResponse;

public interface MemberService {
    MemberProfileResponse getMemberProfile(String email);

    MemberProfileUpdateResponse updateMemberProfile(String email, UpdateMemberProfileRequest request);

    MemberCodeResponse getMemberCode(String email);
}