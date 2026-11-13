package com.rehome.rehome.service;

/** Job nền của gói thành viên và voucher. Tách interface để test gọi trực tiếp không qua @Scheduled. */
public interface MembershipLifecycleService {

    // 00:05 hằng ngày (giờ VN): gói ACTIVE quá hạn -> EXPIRED, tắt nhãn "Nổi bật", gửi thông báo
    void expireSubscriptions();

    // 09:00 hằng ngày: gói sắp hết hạn trong membership_expiry_reminder_days -> nhắc gia hạn,
    // chỉ gửi 1 lần cho mỗi subscription
    void remindExpiringSubscriptions();

    // 00:15 hằng ngày: phát voucher đợt tháng mới cho các gói ACTIVE
    void issueMonthlyVouchers();

    // 00:20 hằng ngày: mã UNUSED quá hạn -> EXPIRED
    void expireVouchers();
}
