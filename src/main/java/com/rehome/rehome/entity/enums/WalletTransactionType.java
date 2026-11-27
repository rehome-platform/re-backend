package com.rehome.rehome.entity.enums;

public enum WalletTransactionType {
    ORDER_PAYMENT,
    ORDER_PAYOUT,
    REFUND,
    WITHDRAWAL,
    WITHDRAWAL_FEE,
    DEGRADED_COMPENSATION,
    COMMISSION_REVENUE,
    WITHDRAWAL_FEE_REVENUE,

    // Member trả tiền mua gói thành viên VIP bằng ví (âm, bên MEMBER)
    MEMBERSHIP_PURCHASE,

    // Nền tảng thu tiền bán gói thành viên (dương, bên PLATFORM). Trả bằng ví thì đây là dòng
    // đối ứng của MEMBERSHIP_PURCHASE; trả bằng VNPay thì đây là dòng ghi nhận doanh thu
    // duy nhất (tiền đi từ ngoài vào, bên MEMBER không có bút toán nào).
    MEMBERSHIP_REVENUE,

    // Chi phí khuyến mãi khi đơn có voucher hoàn tất (âm, bên PLATFORM). CHỈ để thống kê, không
    // đổi số dư quỹ - giống COMMISSION_REVENUE, nên cũng bị loại khỏi đối soát quỹ
    // (xem AdminReportQueryRepository.REVENUE_RECOGNITION_TYPES).
    VOUCHER_DISCOUNT,

    // Người mua trả phí gửi lại hàng trả không hợp lệ bằng ví (âm, bên MEMBER) + dòng đối ứng
    // cùng loại bên PLATFORM (dương). Cũng dùng cho dòng PLATFORM ghi tiền VNPay về muộn trước khi
    // hoàn vào ví (xem ReturnShipBackServiceImpl.refundLateGatewayPayment).
    RETURN_SHIP_BACK_FEE,

    // Ghi nhận doanh thu phí gửi lại hàng trả (dương, bên PLATFORM), không đổi số dư quỹ, không có
    // dòng Member đối ứng - giống WITHDRAWAL_FEE_REVENUE. Bị loại khỏi đối soát quỹ
    // (AdminReportQueryRepository.REVENUE_RECOGNITION_TYPES).
    RETURN_SHIP_BACK_FEE_REVENUE
}
