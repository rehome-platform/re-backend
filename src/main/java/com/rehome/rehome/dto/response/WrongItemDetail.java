package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Thông tin sản phẩm quét nhầm khi pack/scan, để Moderator đưa hàng về đúng chỗ - DG-01
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WrongItemDetail {
    private String productId;
    private String name;
    private String brandName;
    private String size;

    // Mã đơn đang chứa sản phẩm này, null nếu không thuộc đơn nào đang xử lý
    private String belongsToOrderNo;

    private String instructions;
}
