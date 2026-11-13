package com.rehome.rehome.constant;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.rehome.rehome.constant.SystemConfigKeys.*;

/**
 * Danh sách tham số Admin được phép chỉnh (F-157), kèm kiểu và khoảng hợp lệ để trả 422 khi nhập sai.
 * Muốn mở thêm tham số: thêm hằng vào SystemConfigKeys, thêm một dòng ở SPECS và một dòng ở defaults().
 */
public final class SystemConfigCatalog {

    private SystemConfigCatalog() {}

    public enum ValueType { INT, DECIMAL }

    public record Spec(String key, ValueType type, BigDecimal min, BigDecimal max) {}

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private static final Map<String, Spec> SPECS = Map.ofEntries(
            int_(TERM_LENGTH_DAYS, 1, 365),
            int_(MAX_RENEWALS, 0, 10),
            int_(FREE_WITHDRAWAL_WINDOW_DAYS, 0, 365),
            int_(MAX_CONCURRENT_CAMPAIGNS, 1, 100),
            int_(LEGAL_IMAGE_RETENTION_DAYS, 1, 365),
            int_(OVERDUE_TASK_HOURS, 1, 720),
            // Ví và rút tiền: số tiền là đồng
            dec(MIN_PAYOUT_AMOUNT, ONE, new BigDecimal("10000000")),
            int_(MAX_PAYOUT_REQUESTS_PER_DAY, 1, 100),
            // Luồng ký gửi
            int_(END_OF_TERM_DECISION_DAYS, 1, 365),
            int_(DEGRADED_ITEM_DECISION_DAYS, 1, 365),
            int_(BRING_IN_DEADLINE_DAYS, 1, 90),
            int_(MAX_PENDING_FORMS_PER_MEMBER, 1, 50),
            int_(MAX_PRICE_CHANGES_PER_DAY, 1, 20),
            // Đơn hàng và trả hàng
            int_(ORDER_PAYMENT_TIMEOUT_MINUTES, 1, 1440),
            int_(ORDER_AUTO_COMPLETE_DAYS, 1, 30),
            int_(RETURN_REQUEST_WINDOW_DAYS, 1, 30),
            int_(RETURN_HANDOVER_CARRIER_DAYS, 1, 30),
            int_(COUNTER_PICKUP_DAYS, 1, 90),
            // Tỷ lệ lưu dạng phân số: 0.10 = 10%
            dec(COMMISSION_RATE_TERM1, ZERO, ONE),
            dec(COMMISSION_RATE_TERM2, ZERO, ONE),
            dec(COMMISSION_RATE_TERM3, ZERO, ONE),
            dec(DEGRADED_COMPENSATION_RATE, ZERO, ONE),
            dec(FLOOR_PRICE_RATIO, new BigDecimal("0.01"), ONE),
            dec(WITHDRAWAL_FEE, ZERO, new BigDecimal("10000000")),
            // Gói thành viên VIP và voucher
            int_(MEMBERSHIP_EXPIRY_REMINDER_DAYS, 1, 30),
            // Trần 1.00 = voucher giảm tối đa bằng đúng hoa hồng nền tảng thu trên đơn (hòa vốn).
            // Cho phép tới 2.00 để Admin chủ động chịu lỗ trên đơn khi chạy khuyến mãi mạnh.
            dec(VOUCHER_MAX_RATIO_OF_COMMISSION, ZERO, new BigDecimal("2.00")));

    public static Optional<Spec> find(String key) {
        return Optional.ofNullable(SPECS.get(key));
    }

    /**
     * Giá trị mặc định của mọi tham số trong catalog, giữ thứ tự khai báo.
     * Dùng chung cho seed DB (SystemConfigInitializer) và API xem cấu hình (GET /api/admin/config),
     * lấy từ các DEFAULT_* trong SystemConfigKeys để các nơi không bao giờ lệch nhau.
     */
    public static Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(TERM_LENGTH_DAYS, String.valueOf(DEFAULT_TERM_LENGTH_DAYS));
        m.put(MAX_RENEWALS, String.valueOf(DEFAULT_MAX_RENEWALS));
        m.put(FREE_WITHDRAWAL_WINDOW_DAYS, String.valueOf(DEFAULT_FREE_WITHDRAWAL_WINDOW_DAYS));
        m.put(COMMISSION_RATE_TERM1, DEFAULT_COMMISSION_RATE_TERM1.toPlainString());
        m.put(COMMISSION_RATE_TERM2, DEFAULT_COMMISSION_RATE_TERM2.toPlainString());
        m.put(COMMISSION_RATE_TERM3, DEFAULT_COMMISSION_RATE_TERM3.toPlainString());
        m.put(DEGRADED_COMPENSATION_RATE, DEFAULT_DEGRADED_COMPENSATION_RATE.toPlainString());
        m.put(FLOOR_PRICE_RATIO, DEFAULT_FLOOR_PRICE_RATIO.toPlainString());
        m.put(WITHDRAWAL_FEE, DEFAULT_WITHDRAWAL_FEE.toPlainString());
        m.put(MAX_CONCURRENT_CAMPAIGNS, String.valueOf(DEFAULT_MAX_CONCURRENT_CAMPAIGNS));
        m.put(LEGAL_IMAGE_RETENTION_DAYS, String.valueOf(DEFAULT_LEGAL_IMAGE_RETENTION_DAYS));
        m.put(OVERDUE_TASK_HOURS, String.valueOf(DEFAULT_OVERDUE_TASK_HOURS));
        m.put(MIN_PAYOUT_AMOUNT, DEFAULT_MIN_PAYOUT_AMOUNT.toPlainString());
        m.put(MAX_PAYOUT_REQUESTS_PER_DAY, String.valueOf(DEFAULT_MAX_PAYOUT_REQUESTS_PER_DAY));
        m.put(END_OF_TERM_DECISION_DAYS, String.valueOf(DEFAULT_END_OF_TERM_DECISION_DAYS));
        m.put(DEGRADED_ITEM_DECISION_DAYS, String.valueOf(DEFAULT_DEGRADED_ITEM_DECISION_DAYS));
        m.put(BRING_IN_DEADLINE_DAYS, String.valueOf(DEFAULT_BRING_IN_DEADLINE_DAYS));
        m.put(MAX_PENDING_FORMS_PER_MEMBER, String.valueOf(DEFAULT_MAX_PENDING_FORMS_PER_MEMBER));
        m.put(MAX_PRICE_CHANGES_PER_DAY, String.valueOf(DEFAULT_MAX_PRICE_CHANGES_PER_DAY));
        m.put(ORDER_PAYMENT_TIMEOUT_MINUTES, String.valueOf(DEFAULT_ORDER_PAYMENT_TIMEOUT_MINUTES));
        m.put(ORDER_AUTO_COMPLETE_DAYS, String.valueOf(DEFAULT_ORDER_AUTO_COMPLETE_DAYS));
        m.put(RETURN_REQUEST_WINDOW_DAYS, String.valueOf(DEFAULT_RETURN_REQUEST_WINDOW_DAYS));
        m.put(RETURN_HANDOVER_CARRIER_DAYS, String.valueOf(DEFAULT_RETURN_HANDOVER_CARRIER_DAYS));
        m.put(COUNTER_PICKUP_DAYS, String.valueOf(DEFAULT_COUNTER_PICKUP_DAYS));
        m.put(MEMBERSHIP_EXPIRY_REMINDER_DAYS, String.valueOf(DEFAULT_MEMBERSHIP_EXPIRY_REMINDER_DAYS));
        m.put(VOUCHER_MAX_RATIO_OF_COMMISSION, DEFAULT_VOUCHER_MAX_RATIO_OF_COMMISSION.toPlainString());
        return m;
    }

    private static Map.Entry<String, Spec> int_(String key, long min, long max) {
        return Map.entry(key, new Spec(key, ValueType.INT, BigDecimal.valueOf(min), BigDecimal.valueOf(max)));
    }

    private static Map.Entry<String, Spec> dec(String key, BigDecimal min, BigDecimal max) {
        return Map.entry(key, new Spec(key, ValueType.DECIMAL, min, max));
    }
}