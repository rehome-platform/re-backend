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

    // ---- Điểm tích lũy ----
    // Mỗi bao nhiêu đồng của đơn (total_amount, đã trừ voucher) thì được 1 điểm. Đặt 0 hoặc âm để tắt tích điểm.
    public static final String POINTS_VND_PER_POINT = "points_vnd_per_point";
    public static final int DEFAULT_POINTS_VND_PER_POINT = 10000;

    // Số điểm đổi 1 lượt phối đồ AI (ảnh tĩnh)
    public static final String POINTS_COST_PER_AI_TRYON = "points_cost_per_ai_tryon";
    public static final int DEFAULT_POINTS_COST_PER_AI_TRYON = 10;

    // Số điểm đổi 1 lượt thử đồ AI real-time (chi phí thật cao hơn nên mặc định đắt hơn)
    public static final String POINTS_COST_PER_LIVE_TRYON = "points_cost_per_live_tryon";
    public static final int DEFAULT_POINTS_COST_PER_LIVE_TRYON = 20;

    // Số yêu cầu rút tiền tối đa mỗi ngày của một Member
    public static final String MAX_PAYOUT_REQUESTS_PER_DAY = "max_payout_requests_per_day";
    public static final int DEFAULT_MAX_PAYOUT_REQUESTS_PER_DAY = 3;

    // ---- Luồng ký gửi ----

    // Số ngày Member được chọn cách xử lý cuối kỳ (lấy về / ký gửi lại), quá hạn thành hàng vô thừa nhận
    public static final String END_OF_TERM_DECISION_DAYS = "end_of_term_decision_days";
    public static final int DEFAULT_END_OF_TERM_DECISION_DAYS = 30;

    // Số ngày Member được chọn phương án đền bù hàng xuống cấp (F-036), quá hạn thành hàng vô thừa nhận
    public static final String DEGRADED_ITEM_DECISION_DAYS = "degraded_item_decision_days";
    public static final int DEFAULT_DEGRADED_ITEM_DECISION_DAYS = 30;

    // Số ngày Member phải mang đồ đến kho sau khi form ký gửi được duyệt
    public static final String BRING_IN_DEADLINE_DAYS = "bring_in_deadline_days";
    public static final int DEFAULT_BRING_IN_DEADLINE_DAYS = 7;

    // ---- Thương lượng giá ký gửi lại cuối kỳ (negotiation RE_CONSIGN) ----

    // Số ngày Member phải xác nhận hoặc đề xuất lại mỗi lần Moderator đề xuất giá, quá hạn thì
    // coi như từ chối (chuyển TAKE_BACK)
    public static final String RE_CONSIGN_CONFIRM_DAYS = "re_consign_confirm_days";
    public static final int DEFAULT_RE_CONSIGN_CONFIRM_DAYS = 7;

    // Số lần Member tối đa được đề xuất lại giá trong một đợt ký gửi lại
    public static final String RE_CONSIGN_MAX_COUNTER_ROUNDS = "re_consign_max_counter_rounds";
    public static final int DEFAULT_RE_CONSIGN_MAX_COUNTER_ROUNDS = 2;

    // Số ngày Moderator nên phản hồi đề xuất giá của Member, quá hạn chỉ đánh dấu quá hạn trong
    // hàng đợi chứ không tự xử lý
    public static final String RE_CONSIGN_MODERATOR_RESPONSE_DAYS = "re_consign_moderator_response_days";
    public static final int DEFAULT_RE_CONSIGN_MODERATOR_RESPONSE_DAYS = 2;

    // Số form ký gửi đang chờ duyệt tối đa của mỗi Member.
    //
    // ⚠️ KHÔNG còn là nguồn chính: hạn mức này giờ đọc theo GÓI THÀNH VIÊN
    // (membership_plans.max_pending_consignment_forms, Admin sửa ở màn W-01) qua
    // MembershipQuotaResolver — xem ConsignmentFormServiceImpl.maxPendingForms(Account).
    // Key này GIỮ LẠI làm giá trị dự phòng duy nhất cho trường hợp không resolve được gói
    // (membership_plans chưa seed). Nó cố ý vẫn nằm trong SystemConfigCatalog để Admin thấy, nhưng
    // sửa nó sẽ KHÔNG đổi hạn mức của Member nào đang resolve được gói.
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

    // Số phút người mua phải trả phí gửi lại hàng trả không hợp lệ (SHIP_BACK), quá hạn xoá lựa chọn
    public static final String RETURN_SHIP_BACK_FEE_DEADLINE_MINUTES = "return_ship_back_fee_deadline_minutes";
    public static final int DEFAULT_RETURN_SHIP_BACK_FEE_DEADLINE_MINUTES = 30;

    // Số ngày Moderator phải tạo vận đơn gửi lại sau khi người mua trả phí
    public static final String RETURN_SHIP_BACK_SHIP_WITHIN_DAYS = "return_ship_back_ship_within_days";
    public static final int DEFAULT_RETURN_SHIP_BACK_SHIP_WITHIN_DAYS = 2;

    // ---- Gói thành viên VIP và voucher ----
    // Giá gói và hạn mức KHÔNG ở đây mà ở bảng membership_plans: mỗi hạng có một bộ giá trị riêng
    // nên đó là dữ liệu, không phải tham số toàn cục.

    // Số ngày trước khi gói hết hạn thì gửi thông báo nhắc gia hạn (MembershipExpiryReminderJob)
    public static final String MEMBERSHIP_EXPIRY_REMINDER_DAYS = "membership_expiry_reminder_days";
    public static final int DEFAULT_MEMBERSHIP_EXPIRY_REMINDER_DAYS = 3;

    // Trần an toàn: voucher ORDER_DISCOUNT không được giảm quá tỷ lệ này của hoa hồng nền tảng thu
    // được trên đơn. 1.00 = giảm tối đa bằng đúng hoa hồng (nền tảng hòa vốn trên đơn đó).
    public static final String VOUCHER_MAX_RATIO_OF_COMMISSION = "voucher_max_ratio_of_commission";
    public static final BigDecimal DEFAULT_VOUCHER_MAX_RATIO_OF_COMMISSION = new BigDecimal("1.00");
}