package com.rehome.rehome.constant;

import java.math.BigDecimal;

// Tên key trong bảng system_configs. Chỉ định nghĩa ở đây, đừng gõ tay chuỗi ở chỗ khác.
// Thiếu key trong DB thì dùng giá trị mặc định bên dưới.
public final class SystemConfigKeys {

    private SystemConfigKeys() {}

    public static final String TERM_LENGTH_DAYS = "term_length_days";
    public static final int DEFAULT_TERM_LENGTH_DAYS = 30;

    public static final String MAX_RENEWALS = "max_renewals";
    public static final int DEFAULT_MAX_RENEWALS = 2;

    public static final String FREE_WITHDRAWAL_WINDOW_DAYS = "free_withdrawal_window_days";
    public static final int DEFAULT_FREE_WITHDRAWAL_WINDOW_DAYS = 7;

    public static final String COMMISSION_RATE_TERM1 = "commission_rate_term1";
    public static final BigDecimal DEFAULT_COMMISSION_RATE_TERM1 = new BigDecimal("0.10");

    public static final String COMMISSION_RATE_TERM2 = "commission_rate_term2";
    public static final BigDecimal DEFAULT_COMMISSION_RATE_TERM2 = new BigDecimal("0.15");

    public static final String COMMISSION_RATE_TERM3 = "commission_rate_term3";
    public static final BigDecimal DEFAULT_COMMISSION_RATE_TERM3 = new BigDecimal("0.20");

    public static final String DEGRADED_COMPENSATION_RATE = "degraded_compensation_rate";
    public static final BigDecimal DEFAULT_DEGRADED_COMPENSATION_RATE = new BigDecimal("0.50");

    // Giá sàn = agreedPrice * tỷ lệ này
    public static final String FLOOR_PRICE_RATIO = "floor_price_ratio";
    public static final BigDecimal DEFAULT_FLOOR_PRICE_RATIO = new BigDecimal("0.50");

    // Trước mắt miễn phí (0). Admin có thể đổi sau trong system_configs
    public static final String WITHDRAWAL_FEE = "withdrawal_fee";
    public static final BigDecimal DEFAULT_WITHDRAWAL_FEE = BigDecimal.ZERO;

    // Số chiến dịch đang chạy tối đa của mỗi tổ chức (F-099)
    public static final String MAX_CONCURRENT_CAMPAIGNS = "max_concurrent_campaigns";
    public static final int DEFAULT_MAX_CONCURRENT_CAMPAIGNS = 3;

    // Số ngày giữ ảnh giấy tờ sau khi hồ sơ tổ chức được duyệt, hết hạn thì tự xóa (F-139)
    public static final String LEGAL_IMAGE_RETENTION_DAYS = "legal_image_retention_days";
    public static final int DEFAULT_LEGAL_IMAGE_RETENTION_DAYS = 30;

    // Việc nằm trong hàng đợi Moderator quá số giờ này thì tính là quá hạn trên dashboard (F-158/F-095)
    public static final String OVERDUE_TASK_HOURS = "overdue_task_hours";
    public static final int DEFAULT_OVERDUE_TASK_HOURS = 48;

    // Số tiền rút tối thiểu mỗi lần của Member, đơn vị đồng (UC rút tiền)
    public static final String MIN_PAYOUT_AMOUNT = "min_payout_amount";
    public static final BigDecimal DEFAULT_MIN_PAYOUT_AMOUNT = new BigDecimal("50000");

    // Số yêu cầu rút tiền tối đa mỗi ngày của một Member
    public static final String MAX_PAYOUT_REQUESTS_PER_DAY = "max_payout_requests_per_day";
    public static final int DEFAULT_MAX_PAYOUT_REQUESTS_PER_DAY = 3;

    // ---- Luồng ký gửi ----

    // Số ngày Member được chọn cách xử lý cuối kỳ (lấy về / ký gửi lại), quá hạn thành hàng vô thừa nhận
    public static final String END_OF_TERM_DECISION_DAYS = "end_of_term_decision_days";
    public static final int DEFAULT_END_OF_TERM_DECISION_DAYS = 30;

    // Số ngày Member phải mang đồ đến kho sau khi form ký gửi được duyệt
    public static final String BRING_IN_DEADLINE_DAYS = "bring_in_deadline_days";
    public static final int DEFAULT_BRING_IN_DEADLINE_DAYS = 7;

    // Số form ký gửi đang chờ duyệt tối đa của mỗi Member
    public static final String MAX_PENDING_FORMS_PER_MEMBER = "max_pending_forms_per_member";
    public static final int DEFAULT_MAX_PENDING_FORMS_PER_MEMBER = 5;

    // Số lần Member được giảm giá một sản phẩm trong một ngày
    public static final String MAX_PRICE_CHANGES_PER_DAY = "max_price_changes_per_day";
    public static final int DEFAULT_MAX_PRICE_CHANGES_PER_DAY = 1;

    // ---- Đơn hàng và trả hàng ----

    // Số phút người mua phải thanh toán đơn, quá hạn đơn tự hủy
    public static final String ORDER_PAYMENT_TIMEOUT_MINUTES = "order_payment_timeout_minutes";
    public static final int DEFAULT_ORDER_PAYMENT_TIMEOUT_MINUTES = 15;

    // Số ngày sau khi giao hàng thì đơn tự hoàn thành
    public static final String ORDER_AUTO_COMPLETE_DAYS = "order_auto_complete_days";
    public static final int DEFAULT_ORDER_AUTO_COMPLETE_DAYS = 3;

    // Số ngày người mua được gửi yêu cầu trả hàng sau khi nhận hàng
    public static final String RETURN_REQUEST_WINDOW_DAYS = "return_request_window_days";
    public static final int DEFAULT_RETURN_REQUEST_WINDOW_DAYS = 3;

    // Số ngày người mua phải bàn giao hàng trả cho GHN sau khi yêu cầu được duyệt
    public static final String RETURN_HANDOVER_CARRIER_DAYS = "return_handover_carrier_days";
    public static final int DEFAULT_RETURN_HANDOVER_CARRIER_DAYS = 3;

    // Số ngày đến kho nhận hàng rút/trả, quá hạn thì hủy
    public static final String COUNTER_PICKUP_DAYS = "counter_pickup_days";
    public static final int DEFAULT_COUNTER_PICKUP_DAYS = 7;
}