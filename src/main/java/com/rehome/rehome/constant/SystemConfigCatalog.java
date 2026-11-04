package com.rehome.rehome.constant;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static com.rehome.rehome.constant.SystemConfigKeys.*;

/**
 * Danh sách tham số Admin được phép chỉnh (F-157), kèm kiểu và khoảng hợp lệ để trả 422 khi nhập sai.
 * Muốn mở thêm tham số: thêm hằng vào SystemConfigKeys rồi thêm một dòng ở đây.
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
            // Tỷ lệ lưu dạng phân số: 0.10 = 10%
            dec(COMMISSION_RATE_TERM1, ZERO, ONE),
            dec(COMMISSION_RATE_TERM2, ZERO, ONE),
            dec(COMMISSION_RATE_TERM3, ZERO, ONE),
            dec(DEGRADED_COMPENSATION_RATE, ZERO, ONE),
            dec(FLOOR_PRICE_RATIO, new BigDecimal("0.01"), ONE),
            dec(WITHDRAWAL_FEE, ZERO, new BigDecimal("10000000")));

    public static Optional<Spec> find(String key) {
        return Optional.ofNullable(SPECS.get(key));
    }

    private static Map.Entry<String, Spec> int_(String key, long min, long max) {
        return Map.entry(key, new Spec(key, ValueType.INT, BigDecimal.valueOf(min), BigDecimal.valueOf(max)));
    }

    private static Map.Entry<String, Spec> dec(String key, BigDecimal min, BigDecimal max) {
        return Map.entry(key, new Spec(key, ValueType.DECIMAL, min, max));
    }
}
