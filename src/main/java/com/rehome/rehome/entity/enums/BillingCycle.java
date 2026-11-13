package com.rehome.rehome.entity.enums;

// Chu kỳ thanh toán gói thành viên. Số tháng cộng vào expiresAt lấy ở đây, không hard-code ở service.
public enum BillingCycle {
    MONTHLY(1),
    YEARLY(12);

    private final int months;

    BillingCycle(int months) {
        this.months = months;
    }

    public int getMonths() {
        return months;
    }
}
