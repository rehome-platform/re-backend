package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trang công khai của 1 tổ chức đã VERIFIED (F-082). Guest xem được, không cần token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationPublicProfileResponse {
    private String id;
    private String displayName;
    private String description;

    @JsonProperty("isFollowing") // Jackson mặc định bỏ tiền tố "is" của getter boolean -> phải ép tên field tường minh
    private boolean isFollowing;
}
