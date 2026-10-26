package com.rehome.rehome.initializer;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
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
// @Component // tạm tắt: chỉ cần app chạy 1 lần để Hibernate sync schema, chưa cần seed account thật
@Component
@Order(0)
@RequiredArgsConstructor
public class UserInitializer implements CommandLineRunner {

    private static final int REQUIRED_MODERATORS = 3;

    private final AccountRepository accountRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        initAdmin();
        initModerators();
        initMembers();
    }

    private void initAdmin() {
        if (accountRepository.existsByRole(Role.ADMIN)) {
            log.info("An ADMIN account already exists, skipping admin creation");
            return;
        }
        createAccount("admin@rehome.com", "0900000001", "123456", Role.ADMIN);
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
            createAccount(m[0], m[1], "123456", Role.MODERATOR);
            toCreate--;
        }
    }

    // 5 Member để test quầy (tra theo phone / memberCode). Mật khẩu chung: 123456
    // memberCode do @GeneratedMemberCode tự sinh khi lưu MemberProfile
    private void initMembers() {
        List<String[]> members = List.of(
                new String[]{"member1@rehome.com", "0900000011", "Nguyễn Văn An"},
                new String[]{"member2@rehome.com", "0900000012", "Trần Thị Bình"},
                new String[]{"member3@rehome.com", "0900000013", "Lê Hoàng Cường"},
                new String[]{"member4@rehome.com", "0900000014", "Phạm Thu Dung"},
                new String[]{"member5@rehome.com", "0900000015", "Tạ Thị Ngọc Anh"}
        );

        for (String[] m : members) {
            if (accountRepository.existsByEmail(m[0])) {
                log.info("MEMBER account {} already exists, skipping", m[0]);
                continue;
            }
            Account account = createAccount(m[0], m[1], "123456", Role.MEMBER);

            // @MapsId: accountId lấy từ account, không set tay
            MemberProfile profile = MemberProfile.builder()
                    .account(account)
                    .fullName(m[2])
                    .build();
            memberProfileRepository.save(profile);
            log.info("Created MEMBER profile: {} ({}), memberCode={}", m[2], m[0], profile.getMemberCode());
        }
    }

    private Account createAccount(String email, String phone, String rawPassword, Role role) {
        Account account = Account.builder()
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .status(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Created {} account: {}", role, email);
        return saved;
    }
}