package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.dto.response.AccountStatusResponse;
import com.rehome.rehome.dto.response.AdminAccountResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.AuditLogRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.service.AccountAdminService;
import com.rehome.rehome.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountAdminServiceImpl implements AccountAdminService {

    private final AccountRepository accountRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final MemberProfileRepository memberProfileRepository;
    private final ModeratorProfileRepository moderatorProfileRepository;
    private final OrganizationProfileRepository organizationProfileRepository;

    private static final int MAX_PAGE_SIZE = 100;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminAccountResponse> search(String search, Role role, AccountStatus status,
                                                     int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String keyword = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        // Không dùng param null cho chuỗi (Postgres không suy ra được kiểu): "%" = khớp tất cả
        String pattern = "%" + keyword + "%";

        Page<Account> result = accountRepository.searchForAdmin(role, status, pattern,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<UUID> ids = result.getContent().stream().map(Account::getId).toList();
        Map<UUID, MemberProfile> members = memberProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MemberProfile::getAccountId, Function.identity()));
        Map<UUID, ModeratorProfile> moderators = moderatorProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ModeratorProfile::getAccountId, Function.identity()));
        Map<UUID, OrganizationProfile> organizations = organizationProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(OrganizationProfile::getAccountId, Function.identity()));

        return PageResponse.from(result.map(a -> toAdminResponse(a, members, moderators, organizations)));
    }

    private AdminAccountResponse toAdminResponse(Account a,
                                                 Map<UUID, MemberProfile> members,
                                                 Map<UUID, ModeratorProfile> moderators,
                                                 Map<UUID, OrganizationProfile> organizations) {
        String fullName = null;
        String code = null;
        MemberProfile member = members.get(a.getId());
        ModeratorProfile moderator = moderators.get(a.getId());
        OrganizationProfile organization = organizations.get(a.getId());
        if (member != null) {
            fullName = member.getFullName();
            code = member.getMemberCode();
        } else if (moderator != null) {
            fullName = moderator.getFullName();
            code = moderator.getModeratorCode();
        } else if (organization != null) {
            fullName = organization.getDisplayName();
        }
        return AdminAccountResponse.builder()
                .accountId(a.getId())
                .email(a.getEmail())
                .phone(a.getPhone())
                .role(a.getRole())
                .status(a.getStatus())
                .fullName(fullName)
                .code(code)
                .banReason(a.getBanReason())
                .createdAt(a.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public AccountStatusResponse ban(Account admin, UUID targetId, String reason) {
        Account target = accountRepository.findById(targetId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (target.getRole() == Role.ADMIN || target.getId().equals(admin.getId())) {
            throw new AppException(ErrorCode.CANNOT_BAN_ADMIN_OR_SELF);
        }
        if (target.getStatus() == AccountStatus.BANNED) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_BANNED);
        }

        target.setStatus(AccountStatus.BANNED);
        target.setBanReason(reason.trim());
        accountRepository.save(target);

        // Đá khỏi các phiên đang đăng nhập; access token còn hạn bị JwtAuthenticationFilter chặn
        refreshTokenService.revokeAllAccountTokens(target);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("reason", target.getBanReason());
        writeAudit(admin, "BAN_ACCOUNT", target, detail);

        return toResponse(target);
    }

    @Override
    @Transactional
    public AccountStatusResponse unban(Account admin, UUID targetId) {
        Account target = accountRepository.findById(targetId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (target.getStatus() != AccountStatus.BANNED) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_BANNED);
        }

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("previousReason", target.getBanReason());

        target.setStatus(AccountStatus.ACTIVE);
        target.setBanReason(null);
        accountRepository.save(target);

        writeAudit(admin, "UNBAN_ACCOUNT", target, detail);

        return toResponse(target);
    }

    private void writeAudit(Account admin, String action, Account target, ObjectNode detail) {
        auditLogRepository.save(AuditLog.builder()
                .actorAccount(admin)
                .actionType(action)
                .targetType("ACCOUNT")
                .targetId(target.getId().toString())
                .detail(detail)
                .build());
    }

    private AccountStatusResponse toResponse(Account account) {
        return AccountStatusResponse.builder()
                .accountId(account.getId().toString())
                .status(account.getStatus())
                .banReason(account.getBanReason())
                .build();
    }
}
