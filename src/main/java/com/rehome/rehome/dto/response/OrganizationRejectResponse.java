package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết luận từ chối hồ sơ tổ chức (F-138).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationRejectResponse {
    private String id;
    private VerificationStatus verificationStatus;
    private String rejectionReason;
}
