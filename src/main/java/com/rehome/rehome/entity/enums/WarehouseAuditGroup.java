package com.rehome.rehome.entity.enums;

/** Ba nhóm lịch sử thao tác của Moderator kho (GET /moderator/warehouse-history?type=...). */
public enum WarehouseAuditGroup {
    // Đóng gói: nhận/nhả việc, quét SKU, tạo vận đơn
    PACKING,
    // Kiểm định: duyệt/từ chối form, kiểm định, trả tại chỗ, biên nhận, nhập kho
    INSPECTION,
    // Tồn kho: sửa sản phẩm, hàng suy giảm, hết kỳ, ký gửi lại, bàn giao, hàng vô chủ
    INVENTORY
}
