package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationVerifyResponse {
    private String id;
    private VerificationStatus verificationStatus;
    private String verifiedBy;
    private Instant verifiedAt;
}
