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
    DONATION_PICKUP
}