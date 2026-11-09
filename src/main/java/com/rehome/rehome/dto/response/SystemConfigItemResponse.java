package com.rehome.rehome.dto.response;

import com.rehome.rehome.constant.SystemConfigCatalog.Spec;
import com.rehome.rehome.entity.marketplace.SystemConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GET /api/admin/config - một tham số đang áp dụng.
 * Key chưa có dòng trong DB thì value = defaultValue, usingDefault = true, updatedBy/updatedAt = null.
 */
@Getter
@Builder
@AllArgsConstructor
public class SystemConfigItemResponse {
    private final String key;
    // Giá trị đang áp dụng (dòng DB nếu có, không thì mặc định trong code)
    private final String value;
    private final String defaultValue;
    private final boolean usingDefault;
    // INT hoặc DECIMAL, kèm khoảng hợp lệ để web validate ô nhập
    private final String type;
    private final BigDecimal min;
    private final BigDecimal max;
    private final String updatedBy;
    private final Instant updatedAt;

    public static SystemConfigItemResponse of(Spec spec, String defaultValue, SystemConfig stored) {
        return SystemConfigItemResponse.builder()
                .key(spec.key())
                .value(stored != null ? stored.getValue() : defaultValue)
                .defaultValue(defaultValue)
                .usingDefault(stored == null)
                .type(spec.type().name())
                .min(spec.min())
                .max(spec.max())
                .updatedBy(stored != null ? stored.getUpdatedBy().getEmail() : null)
                .updatedAt(stored != null ? stored.getUpdatedAt() : null)
                .build();
    }
}