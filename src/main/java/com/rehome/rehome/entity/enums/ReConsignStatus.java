package com.rehome.rehome.entity.enums;

// Trạng thái thương lượng giá ký gửi lại (EndOfTermDecision.decision = RE_CONSIGN).
// null trên EndOfTermDecision = Member chưa chọn RE_CONSIGN.
public enum ReConsignStatus {
    // Member đã chọn RE_CONSIGN, chờ Moderator chốt giá lần đầu (setReConsignTerms)
    AWAITING_PRICE,
    // Moderator đã đề xuất giá (lần đầu hoặc sau phản hồi), chờ Member xác nhận/đề xuất lại/từ chối
    PROPOSED,
    // Member đã đề xuất giá khác, chờ Moderator phản hồi
    COUNTER_OFFERED,
    // Member đã xác nhận biên nhận (ConsignmentReceiptServiceImpl.confirmReceipt)
    CONFIRMED,
    // Member từ chối, hoặc quá hạn xác nhận/đề xuất -> chuyển TAKE_BACK
    DECLINED,
    // Quá proposalDeadline mà Member không xác nhận/đề xuất lại -> xử lý như DECLINED (job)
    EXPIRED
}
