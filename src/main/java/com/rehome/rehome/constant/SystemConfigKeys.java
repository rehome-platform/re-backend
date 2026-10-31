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
}