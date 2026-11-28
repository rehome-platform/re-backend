package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayOrdersResponse {
    private List<PaidOrderResponse> orders;

    private BigDecimal totalAmount;

    private BigDecimal walletBalance;
}
