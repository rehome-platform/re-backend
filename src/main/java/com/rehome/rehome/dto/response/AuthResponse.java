package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private Role role;

    @Builder.Default
    private String tokenType = "Bearer";
}
