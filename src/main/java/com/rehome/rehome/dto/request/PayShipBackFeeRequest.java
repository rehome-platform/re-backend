package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayShipBackFeeRequest {
    private String method;

    private String redirectUrl;

    public PayShipBackFeeRequest(String method) {
        this.method = method;
    }
}
