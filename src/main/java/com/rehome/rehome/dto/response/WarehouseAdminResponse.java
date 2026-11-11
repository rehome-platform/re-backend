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
    private final String wardName;
    private final String provinceName;
    private final Boolean isNewAddress;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final String openingHours;
    private final CatalogStatus status;
    private final Instant createdAt;
    // Số Moderator ACTIVE đang phụ trách kho
    private final long moderatorCount;
    // Số món đang nằm trong kho (cùng định nghĩa tồn kho với dashboard và API 144)
    private final long inventoryCount;
    // Số đơn đang xử lý tại kho (PAID, PACKING), mỗi đơn đếm một lần
    private final long activeOrders;

    public static WarehouseAdminResponse from(Warehouse w, long moderatorCount, long inventoryCount,
                                              long activeOrders) {
        return WarehouseAdminResponse.builder()
                .id(w.getId())
                .code(w.getCode())
                .name(w.getName())
                .address(w.getAddress())
                .wardName(w.getWardName())
                .provinceName(w.getProvinceName())
                .isNewAddress(w.getIsNewAddress())
                .latitude(w.getLatitude())
                .longitude(w.getLongitude())
                .openingHours(w.getOpeningHours())
                .status(w.getStatus())
                .createdAt(w.getCreatedAt())
                .moderatorCount(moderatorCount)
                .inventoryCount(inventoryCount)
                .activeOrders(activeOrders)
                .build();
    }
}
