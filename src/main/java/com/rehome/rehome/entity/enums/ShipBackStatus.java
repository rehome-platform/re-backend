package com.rehome.rehome.entity.enums;

// Trạng thái luồng ReHome gửi lại hàng trả không hợp lệ qua GHN (ReturnRequest.shipBackStatus).
// Trạng thái chính của yêu cầu vẫn là INVALID trong suốt luồng này.
//   (chọn SHIP_BACK) -> AWAITING_PAYMENT --trả phí--> PAID --Moderator tạo vận đơn--> SHIPPING
//   SHIPPING --GHN delivered--> DELIVERED
//   SHIPPING --GHN return/returned--> FAILED (chỉ còn đến kho lấy, không gửi lại lần 2)
//   AWAITING_PAYMENT quá hạn trả phí -> xoá lựa chọn (shipBackStatus = null)
public enum ShipBackStatus {
    AWAITING_PAYMENT,
    PAID,
    SHIPPING,
    DELIVERED,
    FAILED
}
