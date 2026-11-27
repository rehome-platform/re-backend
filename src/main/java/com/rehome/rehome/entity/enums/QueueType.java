package com.rehome.rehome.entity.enums;

public enum QueueType {
    FORM_REVIEW,
    INSPECTION,
    PACKING,
    RETURN,
    ORG_VERIFICATION,
    LEGAL_CHANGE,
    VIOLATION_REPORT,
    WITHDRAWAL,
    // Member quyên góp sản phẩm ký gửi (POST /products/{id}/donate) - Moderator của kho giữ hàng
    // cần biết để đến lấy món, refId là id của Donation. Đóng ở DonationServiceImpl.handoverDonations.
    DONATION_PICKUP,
    DONATION_REASSIGN,
    DONATION_PREP,
    // Người mua đã trả phí gửi lại hàng trả không hợp lệ - Moderator của kho cần tạo vận đơn GHN
    // trong ship-back-ship-within-days ngày. refId là id ReturnRequest, đóng ở
    // ReturnShipBackServiceImpl.ship.
    RETURN_SHIP_BACK,
    // Member vừa đề xuất giá khác (counter-offer) cho đợt ký gửi lại cuối kỳ - Moderator của kho
    // cần phản hồi trong re_consign_moderator_response_days ngày. refId là id EndOfTermDecision,
    // đóng (dequeue) ở EndOfTermDecisionServiceImpl.counterResponse/decline.
    RECONSIGN_COUNTER
}