package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Đổi địa chỉ giao hàng của một đơn đang chờ xử lý - PATCH /orders/{id}/shipping-address
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderShippingAddressRequest {
    private String shippingAddressId;
}
