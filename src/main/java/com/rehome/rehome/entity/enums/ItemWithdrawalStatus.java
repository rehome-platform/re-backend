package com.rehome.rehome.entity.enums;

public enum ItemWithdrawalStatus {
    // Mới tạo, chờ Moderator của kho giữ sản phẩm duyệt
    PENDING_APPROVAL,
    // Đã duyệt, chờ Member đến kho lấy hàng (hoặc trả phí trước, nếu có)
    PENDING_PICKUP,
    // Moderator từ chối, có lý do - trạng thái cuối
    REJECTED,
    COMPLETED,
    EXPIRED,
    // Member đổi ý sang quyên góp sản phẩm (ProductServiceImpl.donate) khi yêu cầu còn PENDING_PICKUP
    CANCELLED,
    CONVERTED_TO_DONATION
}
