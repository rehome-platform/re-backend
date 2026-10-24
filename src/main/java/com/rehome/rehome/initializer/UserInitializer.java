package com.rehome.rehome.initializer;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class UserInitializer implements CommandLineRunner {

    private static final int REQUIRED_MODERATORS = 3;

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init.admin-password:123456}")
    private String adminPassword;

    @Value("${app.init.moderator-password:123456}")
    private String moderatorPassword;

    @Override
    @Transactional
    public void run(String... args) {
        initAdmin();
        initModerators();
    }

    private void initAdmin() {
        if (accountRepository.existsByRole(Role.ADMIN)) {
            log.info("An ADMIN account already exists, skipping admin creation");
            return;
        }
        createAccount("admin@rehome.com", "0900000001", adminPassword, Role.ADMIN);
    }

    private void initModerators() {
        long existing = accountRepository.countByRole(Role.MODERATOR);
        if (existing >= REQUIRED_MODERATORS) {
            log.info("Already have {} MODERATOR accounts, skipping moderator creation", existing);
            return;
        }

        List<String[]> moderators = List.of(
                new String[]{"moderator1@rehome.com", "0900000002"},
                new String[]{"moderator2@rehome.com", "0900000003"},
                new String[]{"moderator3@rehome.com", "0900000004"}
        );

        long toCreate = REQUIRED_MODERATORS - existing;
        for (String[] m : moderators) {
            if (toCreate <= 0) break;
            if (accountRepository.existsByEmail(m[0])) continue;
            createAccount(m[0], m[1], moderatorPassword, Role.MODERATOR);
            toCreate--;
        }
    }

    private void createAccount(String email, String phone, String rawPassword, Role role) {
        Account account = Account.builder()
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .status(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build();

        accountRepository.save(account);
        log.info("Created {} account: {}", role, email);
    }
}