package com.rehome.rehome.initializer;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.SystemConfig;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.rehome.rehome.constant.SystemConfigKeys.*;

/**
 * Seed các tham số cấu hình mặc định vào system_configs để Admin thấy/sửa được ngay (F-157).
 * Chỉ tạo key CHƯA có dòng; không bao giờ ghi đè giá trị Admin đã sửa.
 * Chạy sau UserInitializer (@Order(0)) vì updated_by bắt buộc trỏ tới một tài khoản Admin.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SystemConfigInitializer implements CommandLineRunner {

    private final SystemConfigRepository systemConfigRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Account admin = accountRepository.findFirstByRole(Role.ADMIN).orElse(null);
        if (admin == null) {
            log.warn("Chưa có tài khoản ADMIN, bỏ qua seed system_configs");
            return;
        }

        int created = 0;
        for (Map.Entry<String, String> e : defaults().entrySet()) {
            if (systemConfigRepository.findByKey(e.getKey()).isPresent()) continue;
            systemConfigRepository.save(SystemConfig.builder()
                    .key(e.getKey())
                    .value(e.getValue())
                    .updatedBy(admin)
                    .build());
            created++;
        }
        log.info("Seed system_configs: tạo {} tham số mới", created);
    }

    // Giá trị lấy từ các DEFAULT_* trong SystemConfigKeys để hai nơi không bao giờ lệch nhau
    private static Map<String, String> defaults() {
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
        return m;
    }
}