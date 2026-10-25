package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Thanh toán một hoặc nhiều đơn bằng Ví ReHome trong cùng một request.
// MoMo/VNPay không đi qua đây mà qua /api/momo/create, /api/vnpay/create.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayOrdersRequest {
    private List<String> orderIds;

    // Hiện chỉ nhận WALLET
    private String method;
}
