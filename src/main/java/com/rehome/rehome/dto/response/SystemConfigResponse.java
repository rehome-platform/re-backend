package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.marketplace.SystemConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** API 146 - tham số sau khi sửa. */
@Getter
@Builder
@AllArgsConstructor
public class SystemConfigResponse {
    private final String key;
    private final String value;
    private final String updatedBy;
    private final Instant updatedAt;

    public static SystemConfigResponse from(SystemConfig c) {
        return SystemConfigResponse.builder()
                .key(c.getKey())
                .value(c.getValue())
                .updatedBy(c.getUpdatedBy().getEmail())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
