package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    private String shippingAddressId;

    // Bỏ trống = lấy cả giỏ hàng; truyền đúng 1 id = "Mua ngay", bỏ qua giỏ
    private List<String> productIds;

    // Ghi chú cho kho đóng gói (vd: giao giờ hành chính, gói kỹ...) - tùy chọn, hiển thị ở DG-01
    private String buyerNote;
}
