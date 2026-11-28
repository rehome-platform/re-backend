package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.VerificationStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationResubmitResponse {
    private VerificationStatus verificationStatus;
}
