package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.MembershipTier;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrantMembershipRequest {

    private UUID memberId;
    private MembershipTier tier;
    private Integer months;
    private String reason;
}
