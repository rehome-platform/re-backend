package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.MemberProfileResponse;

public interface MemberService {
    MemberProfileResponse getMemberProfile(String email);
}