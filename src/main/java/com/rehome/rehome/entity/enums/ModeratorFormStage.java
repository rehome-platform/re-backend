package com.rehome.rehome.entity.enums;

// Giá trị của query param stage ở GET /moderator/consignment-forms (mỗi giá trị ứng với một tab của Moderator).
// Không phải trạng thái lưu trong DB, và không thay thế ConsignmentFormStatus.
public enum ModeratorFormStage {
    // Tab "Chờ duyệt": form PENDING
    PENDING,
    // Tab "Đã duyệt": form APPROVED, chờ Member mang đồ tới kho
    APPROVED,
    // Tab "Đang kiểm định": đang kiểm định + chờ Member xác nhận biên nhận + đã xác nhận nhưng chưa nhập kho
    INSPECTING,
    // Tab "Nhập kho hôm nay": sản phẩm có product.createdAt thuộc ngày hôm nay theo giờ Việt Nam
    STOCKED_TODAY
}