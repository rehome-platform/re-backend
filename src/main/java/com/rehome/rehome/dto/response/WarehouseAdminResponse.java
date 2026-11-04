package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.marketplace.Warehouse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** API 141, 142, 143, 144 - kho dưới góc nhìn Admin (có cả kho HIDDEN). */
@Getter
@Builder
@AllArgsConstructor
public class WarehouseAdminResponse {
    private final UUID id;
    private final String code;
    private final String name;
    private final String address;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final String openingHours;
    private final CatalogStatus status;
    private final Instant createdAt;

    public static WarehouseAdminResponse from(Warehouse w) {
        return WarehouseAdminResponse.builder()
                .id(w.getId())
                .code(w.getCode())
                .name(w.getName())
                .address(w.getAddress())
                .latitude(w.getLatitude())
                .longitude(w.getLongitude())
                .openingHours(w.getOpeningHours())
                .status(w.getStatus())
                .createdAt(w.getCreatedAt())
                .build();
    }
}
