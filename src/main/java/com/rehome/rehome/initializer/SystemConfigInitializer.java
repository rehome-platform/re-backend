package com.rehome.rehome.initializer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.initializer.seed-enabled", havingValue = "true")
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