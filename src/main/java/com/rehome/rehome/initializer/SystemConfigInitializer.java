package com.rehome.rehome.initializer;

import com.rehome.rehome.constant.SystemConfigCatalog;
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

import java.util.Map;

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
        for (Map.Entry<String, String> e : SystemConfigCatalog.defaults().entrySet()) {
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
}