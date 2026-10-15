package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberProfileResponse {
    private UUID accountId;
    private String email;
    private String phone;
    private String fullName;
    private String avatarUrl;
    private String memberCode;
    private boolean emailVerified;
}