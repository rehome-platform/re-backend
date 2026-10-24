package com.rehome.rehome.dto.response;

// Cặp phường/xã + tỉnh/thành đã khớp với danh mục GHN hệ 2 cấp, tên đã chuẩn hóa về đúng
// giá trị GHN trả về. Chỉ những tên này mới dùng được cho to_*_name/from_*_name khi tạo vận đơn
// với is_new_*_address: true - gửi tên tự do (vd "TP. Hồ Chí Minh") sẽ bị TO_ADDRESS_CONFLICT.
public record ResolvedAddressUnit(int provinceId, String provinceName, String wardName) {
}
