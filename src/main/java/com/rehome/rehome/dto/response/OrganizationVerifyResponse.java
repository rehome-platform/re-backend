package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Kết luận xác minh tổ chức của Moderator (F-138).
 */
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
