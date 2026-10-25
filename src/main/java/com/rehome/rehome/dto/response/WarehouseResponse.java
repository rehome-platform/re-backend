package com.rehome.rehome.dto.marketplace.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseResponse {
    private UUID id;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String openingHours;
}