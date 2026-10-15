package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.VerificationStatus;
import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegisterOrganizationResponse {
    private UUID accountId;
    private VerificationStatus verificationStatus;
}
