package com.rehome.rehome.entity.enums;

// Một dòng trong lịch sử thương lượng giá ký gửi lại (re_consign_negotiation_history.action)
public enum ReConsignNegotiationAction {
    PROPOSE,        // Moderator chốt giá lần đầu (setReConsignTerms)
    COUNTER_OFFER,  // Member đề xuất giá khác
    ACCEPT,         // Moderator đồng ý giá Member đề xuất
    REPROPOSE,      // Moderator đề xuất giá khác sau khi Member counter-offer
    KEEP,           // Moderator giữ nguyên giá đã đề xuất
    DECLINE,        // Member không đồng ý -> TAKE_BACK
    EXPIRE          // Quá hạn xác nhận -> TAKE_BACK (job)
}
