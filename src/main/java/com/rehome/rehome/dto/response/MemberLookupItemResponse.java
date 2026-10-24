package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.auth.MemberProfile;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberLookupItemResponse {
    private UUID accountId;
    private String fullName;
    private String avatarUrl;
    private String memberCode;

    public static MemberLookupItemResponse from(MemberProfile p) {
        return MemberLookupItemResponse.builder()
                .accountId(p.getAccountId())
                .fullName(p.getFullName())
                .avatarUrl(p.getAvatarUrl())
                .memberCode(p.getMemberCode())
                .build();
    }
}