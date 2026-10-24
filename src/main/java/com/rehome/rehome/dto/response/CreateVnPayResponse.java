package com.rehome.rehome.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateVnPayResponse {
    private String txnRef;
    private BigDecimal amount;
    private String payUrl;
}
