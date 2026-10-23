package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Một đơn vị hành chính (tỉnh/huyện/xã) đã chuẩn hóa cho FE:
// code dùng để gọi cấp tiếp theo, name là giá trị gửi lại khi tạo địa chỉ giao hàng
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressUnitResponse {
    private String code;
    private String name;
}
