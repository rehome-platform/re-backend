package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipBackQuoteResponse {
    private BigDecimal fee;
    private String addressId;
    private String recipientName;
    private String phone;
    private String addressDetail;
    private String wardName;
    private String provinceName;
    private String warehouseName;
    private Integer feeDeadlineMinutes;
}
