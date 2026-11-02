package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tổ chức đăng bài, nhúng trong PostDetailResponse (F-086).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostOrganizationSummaryResponse {
    private String id;
    private String displayName;
    private String logoUrl;
}
