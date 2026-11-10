package com.rehome.rehome.entity.enums;

// Hành động Moderator chọn khi resolve 1 báo cáo vi phạm (POST /violation-reports/{id}/resolve)
public enum ViolationResolutionAction {
    // target phải là POST: chuyển bài viết sang ContentStatus.REMOVED
    REMOVE_POST,
    // target phải là CAMPAIGN: chuyển chiến dịch sang CampaignStatus.HIDDEN
    HIDE_CAMPAIGN,
    // Mọi loại target: chỉ cảnh báo tổ chức, không đổi trạng thái nội dung
    WARN_ORG,
    // Mọi loại target: không tự khoá tài khoản (chỉ Admin có quyền ban) - ghi audit log riêng
    // actionType để Admin tra trong GET /api/admin/audit-logs như một "danh sách đề xuất khoá"
    PROPOSE_BAN
}
