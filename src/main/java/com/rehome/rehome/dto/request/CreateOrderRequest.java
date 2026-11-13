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

    /**
     * Mã giảm giá, tùy chọn. <b>Một lần thanh toán dùng tối đa MỘT mã</b>, và mã áp cho CẢ lần
     * thanh toán chứ không cho một đơn cụ thể: giỏ nhiều món bị tách thành nhiều đơn thì server tự
     * phân bổ số tiền giảm về từng đơn (xem VoucherCalculator). Người mua không phải chọn áp mã
     * cho món nào.
     */
    private String voucherCode;

    /**
     * Giữ lại dạng 3 tham số của @AllArgsConstructor trước khi có {@code voucherCode}, để các nơi
     * đang dựng request theo thứ tự cũ (test đặt hàng) không phải sửa hàng loạt.
     * Đặt hàng không dùng mã thì đây vẫn là constructor đúng nghĩa, không phải tiện tay.
     */
    public CreateOrderRequest(String shippingAddressId, List<String> productIds, String buyerNote) {
        this(shippingAddressId, productIds, buyerNote, null);
    }
}
