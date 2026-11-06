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
import com.rehome.rehome.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    private static final String DEFAULT_SORT_PROPERTY = "createdAt";
    // Chỉ cho sắp xếp theo các thuộc tính này: không đưa thẳng tham số của client vào Sort
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("createdAt", "status");

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminAccountResponse> search(String search, Role role, AccountStatus status,
                                                     String sortBy, String sortDir, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // Từ khoá giống số điện thoại (+84 912 345 678, 0912-345-678...) được chuẩn hóa về 0912345678
        // để khớp Account.phone; email/họ tên vẫn tìm bình thường
        String keyword = PhoneNumbers.toSearchKeyword(search == null ? "" : search.trim().toLowerCase(Locale.ROOT));
        // Không dùng param null cho chuỗi (Postgres không suy ra được kiểu): "%" = khớp tất cả
        String pattern = "%" + keyword + "%";

        Page<Account> result = accountRepository.searchForAdmin(role, status, pattern,
                PageRequest.of(safePage, safeSize, buildSort(sortBy, sortDir)));

        List<UUID> ids = result.getContent().stream().map(Account::getId).toList();
        Map<UUID, MemberProfile> members = memberProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MemberProfile::getAccountId, Function.identity()));
        Map<UUID, ModeratorProfile> moderators = moderatorProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ModeratorProfile::getAccountId, Function.identity()));
        Map<UUID, OrganizationProfile> organizations = organizationProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(OrganizationProfile::getAccountId, Function.identity()));

        return PageResponse.from(result.map(a -> toAdminResponse(a, members, moderators, organizations)));
    }

    // sortBy/sortDir ngoài danh sách cho phép được đưa về mặc định (createdAt, desc).
    // Sắp theo status có ít giá trị nên thêm createdAt giảm dần làm khoá phụ để các trang không bị lặp/sót dòng.
    static Sort buildSort(String sortBy, String sortDir) {
        String property = sortBy != null && SORTABLE_PROPERTIES.contains(sortBy.trim())
                ? sortBy.trim() : DEFAULT_SORT_PROPERTY;
        Sort.Direction direction = sortDir != null && "asc".equalsIgnoreCase(sortDir.trim())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, property);
        return DEFAULT_SORT_PROPERTY.equals(property)
                ? sort
                : sort.and(Sort.by(Sort.Direction.DESC, DEFAULT_SORT_PROPERTY));
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

        AccountStatus beforeStatus = target.getStatus();
        target.setStatus(AccountStatus.BANNED);
        target.setBanReason(reason.trim());
        accountRepository.save(target);

        // Đá khỏi các phiên đang đăng nhập; access token còn hạn bị JwtAuthenticationFilter chặn
        refreshTokenService.revokeAllAccountTokens(target);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("reason", target.getBanReason());
        detail.put("beforeStatus", beforeStatus.name());
        detail.put("afterStatus", target.getStatus().name());
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
        detail.put("beforeStatus", target.getStatus().name());

        target.setStatus(AccountStatus.ACTIVE);
        target.setBanReason(null);
        accountRepository.save(target);

        detail.put("afterStatus", target.getStatus().name());
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
