package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả bàn giao các lượt quyên góp cho tổ chức (F-165).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonationHandoverResponse {
    private int handedOverCount;
    private String organizationId;
    private String status;
}
