package com.rehome.rehome.entity.enums;

// Moderator phản hồi đề xuất giá của Member (POST /end-of-term-decisions/{id}/counter-response),
// cũng là giá trị lưu ở EndOfTermDecision.moderatorResponse (phản hồi gần nhất).
public enum ReConsignCounterResponseAction {
    // Đồng ý giá Member đề xuất: agreedPrice = memberCounterPrice
    ACCEPT,
    // Đề xuất một giá khác với giá Member vừa đưa ra
    REPROPOSE,
    // Giữ nguyên giá đã đề xuất trước đó, bắt buộc có note giải thích lý do
    KEEP
}
