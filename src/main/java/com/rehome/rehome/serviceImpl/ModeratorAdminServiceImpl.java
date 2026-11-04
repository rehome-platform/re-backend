package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.dto.request.ChangeModeratorWarehouseRequest;
import com.rehome.rehome.dto.request.CreateModeratorRequest;
import com.rehome.rehome.dto.response.ModeratorAdminResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import com.rehome.rehome.repository.QueueItemRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import com.rehome.rehome.service.ModeratorAdminService;
import com.rehome.rehome.service.OtpEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModeratorAdminServiceImpl implements ModeratorAdminService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int TEMP_PASSWORD_LENGTH = 12;
    // Bỏ các ký tự dễ nhầm (0/O, 1/l/I) vì Moderator phải gõ lại từ email
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final ModeratorProfileRepository moderatorProfileRepository;
    private final WarehouseRepository warehouseRepository;
    private final QueueItemRepository queueItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpEmailService otpEmailService;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;
    @Override
    @Transactional
    public ModeratorAdminResponse create(Account admin, CreateModeratorRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String fullName = request.getFullName().trim();

        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        Warehouse warehouse = requireActiveWarehouse(request.getWarehouseId());

        String tempPassword = generateTempPassword();
        Account account = accountRepository.save(Account.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(tempPassword))
                .role(Role.MODERATOR)
                .status(AccountStatus.ACTIVE)
                .emailVerified(true)
                .warehouse(warehouse)
                .build());

        // @MapsId lấy accountId từ account; moderatorCode do @GeneratedModeratorCode sinh khi insert
        ModeratorProfile profile = moderatorProfileRepository.saveAndFlush(ModeratorProfile.builder()
                .account(account)
                .fullName(fullName)
                .warehouse(warehouse)
                .build());

        log.info("moderatorCode = {}", profile.getModeratorCode());
        // Gửi trong transaction: gửi mail lỗi thì rollback, Admin tạo lại được (email chưa bị chiếm)
        otpEmailService.sendModeratorAccount(email, fullName, profile.getModeratorCode(), tempPassword);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("email", email);
        detail.put("fullName", fullName);
        detail.put("moderatorCode", profile.getModeratorCode());
        detail.put("warehouseId", warehouse.getId().toString());
        auditLogger.log(admin, "CREATE_MODERATOR", "ACCOUNT", account.getId().toString(), detail);

        return toResponse(account, profile, warehouse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ModeratorAdminResponse> list(UUID warehouseId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Account> accounts = warehouseId == null
                ? accountRepository.findByRole(Role.MODERATOR, pageable)
                : accountRepository.findByRoleAndWarehouse_Id(Role.MODERATOR, warehouseId, pageable);

        List<UUID> ids = accounts.getContent().stream().map(Account::getId).toList();
        Map<UUID, ModeratorProfile> profiles = moderatorProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ModeratorProfile::getAccountId, Function.identity()));

        return PageResponse.from(accounts.map(a -> {
            ModeratorProfile profile = profiles.get(a.getId());
            // Ưu tiên accounts.warehouse_id, fallback moderator_profiles (giống AccountMeServiceImpl)
            Warehouse warehouse = a.getWarehouse() != null ? a.getWarehouse()
                    : (profile != null ? profile.getWarehouse() : null);
            return toResponse(a, profile, warehouse);
        }));
    }

    @Override
    @Transactional
    public ModeratorAdminResponse changeWarehouse(Account admin, UUID moderatorId,
                                                  ChangeModeratorWarehouseRequest request) {
        Account moderator = accountRepository.findWithWarehouseById(moderatorId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (moderator.getRole() != Role.MODERATOR) {
            throw new AppException(ErrorCode.NOT_MODERATOR_ACCOUNT);
        }

        Warehouse target = requireActiveWarehouse(request.getWarehouseId());
        ModeratorProfile profile = moderatorProfileRepository.findById(moderatorId).orElse(null);

        Warehouse current = moderator.getWarehouse() != null ? moderator.getWarehouse()
                : (profile != null ? profile.getWarehouse() : null);
        if (current != null && current.getId().equals(target.getId())) {
            throw new AppException(ErrorCode.MODERATOR_SAME_WAREHOUSE);
        }

        // Lần đầu được gán kho (chưa có kho cũ) thì cho qua; có kho cũ mà còn giữ việc thì chặn
        if (current != null && queueItemRepository.existsByClaimedBy_Id(moderatorId)) {
            throw new AppException(ErrorCode.MODERATOR_HAS_CLAIMED_TASK);
        }

        moderator.setWarehouse(target);
        accountRepository.save(moderator);
        if (profile != null) {
            profile.setWarehouse(target);
            moderatorProfileRepository.save(profile);
        }

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("fromWarehouseId", current != null ? current.getId().toString() : null);
        detail.put("toWarehouseId", target.getId().toString());
        auditLogger.log(admin, "CHANGE_MODERATOR_WAREHOUSE", "ACCOUNT", moderatorId.toString(), detail);

        return toResponse(moderator, profile, target);
    }

    private Warehouse requireActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));
        if (warehouse.getStatus() == CatalogStatus.HIDDEN) {
            throw new AppException(ErrorCode.WAREHOUSE_HIDDEN);
        }
        return warehouse;
    }

    private static String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private static ModeratorAdminResponse toResponse(Account account, ModeratorProfile profile, Warehouse warehouse) {
        return ModeratorAdminResponse.builder()
                .accountId(account.getId())
                .email(account.getEmail())
                .fullName(profile != null ? profile.getFullName() : null)
                .moderatorCode(profile != null ? profile.getModeratorCode() : null)
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
