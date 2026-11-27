package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayShipBackFeeRequest {
    // WALLET / VNPAY
    private String method;

    // Tuỳ chọn: URL VNPay redirect về sau khi trả (app deep link); bỏ trống dùng vnpay.return-url
    private String redirectUrl;

    public PayShipBackFeeRequest(String method) {
        this.method = method;
    }
}
