package com.rehome.rehome.entity.enums;

// Bước nghiệp vụ tiếp theo Frontend cần mở khi Moderator chọn một form.
// Được dẫn xuất từ status của form + biên nhận mới nhất + sản phẩm, KHÔNG lưu trong DB.
public enum ModeratorResumeStep {
    // Form PENDING: duyệt hoặc từ chối form
    REVIEW_FORM,
    // Form APPROVED: chờ Member mang đồ tới, Moderator bắt đầu kiểm định
    START_INSPECTION,
    // Form INSPECTING, chưa có biên nhận: thực hiện kiểm định và lập biên nhận
    INSPECTION,
    // Đã lập biên nhận, Member chưa xác nhận
    AWAIT_MEMBER_CONFIRM,
    // Member đã xác nhận biên nhận, chưa có sản phẩm: nhập kho
    STOCK_IN,
    // Đã nhập kho xong, không còn bước nào
    COMPLETED,
    // Form đã đóng (REJECTED / EXPIRED / CANCELLED), không còn bước nào
    NONE
}