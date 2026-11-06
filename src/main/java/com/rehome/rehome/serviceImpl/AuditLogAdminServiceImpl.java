package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.AuditLogFilterOptionsResponse;
import com.rehome.rehome.dto.response.AuditLogResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.AuditLogRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.spec.AuditLogSpecifications;
import com.rehome.rehome.service.AuditLogAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogAdminServiceImpl implements AuditLogAdminService {

    static final int MAX_PAGE_SIZE = 100;
    // Số tài khoản tối đa lấy ra khi khớp tên với từ khoá tìm kiếm
    static final int MAX_NAME_MATCHES = 100;

    private final AuditLogRepository auditLogRepository;
    private final AccountRepository accountRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final ModeratorProfileRepository moderatorProfileRepository;
    private final OrganizationProfileRepository organizationProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(UUID actorAccountId, String search, String actionType,
                                                 List<String> actionTypes, String targetType, String targetId,
                                                 Instant from, Instant to, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGINATION);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }

        String keyword = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        // Tìm theo tên: tra trước id các tài khoản có họ tên/tên tổ chức khớp, rồi so với actor và targetId.
        // Không có từ khoá thì không truy vấn thêm.
        List<UUID> matchedAccountIds = keyword == null
                ? List.of()
                : accountRepository.findIdsByProfileNameLike("%" + keyword + "%",
                        PageRequest.of(0, MAX_NAME_MATCHES));

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> logs = auditLogRepository.findAll(
                AuditLogSpecifications.filter(actorAccountId, keyword, matchedAccountIds,
                        mergeActionTypes(actionType, actionTypes), targetType, targetId, from, to),
                pageable);

        // Nạp actor theo lô cho cả trang (4 truy vấn cố định) thay vì từng dòng log
        Set<UUID> actorIds = logs.getContent().stream()
                .map(l -> l.getActorAccount().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Account> accounts = loadAccounts(actorIds);
        Map<UUID, String> names = loadNames(accounts.keySet());

        return PageResponse.of(logs.map(l -> {
            UUID actorId = l.getActorAccount().getId();
            return AuditLogResponse.from(l, accounts.get(actorId), names.get(actorId));
        }));
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogFilterOptionsResponse filterOptions() {
        List<String> actionTypes = auditLogRepository.findDistinctActionTypes();

        Map<UUID, Account> accounts = loadAccounts(auditLogRepository.findDistinctActorIds());
        Map<UUID, String> names = loadNames(accounts.keySet());

        List<AuditLogFilterOptionsResponse.ActorOption> actors = accounts.values().stream()
                .map(a -> AuditLogFilterOptionsResponse.ActorOption.builder()
                        .id(a.getId())
                        .name(names.get(a.getId()))
                        .email(a.getEmail())
                        .role(a.getRole())
                        .build())
                .sorted(Comparator.comparing(
                        (AuditLogFilterOptionsResponse.ActorOption o) -> displayKey(o.getName(), o.getEmail())))
                .toList();

        return AuditLogFilterOptionsResponse.builder()
                .actors(actors)
                .actionTypes(actionTypes)
                .build();
    }

    // actionType (cũ, một giá trị) + actionTypes (mới, nhiều giá trị) -> một tập, bỏ giá trị rỗng/trùng
    private static Set<String> mergeActionTypes(String actionType, List<String> actionTypes) {
        Set<String> merged = new LinkedHashSet<>();
        if (actionType != null && !actionType.isBlank()) {
            merged.add(actionType.trim());
        }
        if (actionTypes != null) {
            actionTypes.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .forEach(merged::add);
        }
        return merged;
    }

    private Map<UUID, Account> loadAccounts(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return accountRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    // Họ tên Member/Moderator hoặc tên tổ chức; Admin không có profile nên không có tên
    private Map<UUID, String> loadNames(Collection<UUID> accountIds) {
        Map<UUID, String> names = new HashMap<>();
        if (accountIds.isEmpty()) {
            return names;
        }
        for (OrganizationProfile o : organizationProfileRepository.findAllById(accountIds)) {
            names.put(o.getAccountId(), o.getDisplayName());
        }
        for (ModeratorProfile m : moderatorProfileRepository.findAllById(accountIds)) {
            names.put(m.getAccountId(), m.getFullName());
        }
        // Cùng thứ tự ưu tiên với AccountAdminServiceImpl: Member > Moderator > Tổ chức
        for (MemberProfile m : memberProfileRepository.findAllById(accountIds)) {
            names.put(m.getAccountId(), m.getFullName());
        }
        return names;
    }

    private static String displayKey(String name, String email) {
        String key = name != null && !name.isBlank() ? name : email;
        return key == null ? "" : key.toLowerCase(Locale.ROOT);
    }
}
