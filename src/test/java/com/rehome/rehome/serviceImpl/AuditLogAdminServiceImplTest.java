package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.dto.response.AuditLogFilterOptionsResponse;
import com.rehome.rehome.dto.response.AuditLogResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.AuditLogRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** API 149 - tra cứu audit log: thông tin actor nạp theo lô, bộ lọc thời gian, filter-options. */
@ExtendWith(MockitoExtension.class)
class AuditLogAdminServiceImplTest {

    @Mock AuditLogRepository auditLogRepository;
    @Mock AccountRepository accountRepository;
    @Mock MemberProfileRepository memberProfileRepository;
    @Mock ModeratorProfileRepository moderatorProfileRepository;
    @Mock OrganizationProfileRepository organizationProfileRepository;

    @InjectMocks AuditLogAdminServiceImpl service;

    private final ObjectMapper mapper = new ObjectMapper();

    private Account account(Role role, String email) {
        return Account.builder().id(UUID.randomUUID()).email(email).role(role)
                .status(AccountStatus.ACTIVE).build();
    }

    private AuditLog log(Account actor, String action, String targetId) {
        return AuditLog.builder().id(UUID.randomUUID()).actorAccount(actor).actionType(action)
                .targetType("ACCOUNT").targetId(targetId).createdAt(Instant.parse("2026-11-01T00:00:00Z")).build();
    }

    @Test
    @DisplayName("search: actor được nạp theo lô một lần cho cả trang (không N+1), có actorName/Email/Role")
    @SuppressWarnings("unchecked")
    void search_loadsActorsInBatch() {
        Account admin = account(Role.ADMIN, "admin@rehome.com");
        Account mod = account(Role.MODERATOR, "mod@rehome.com");
        // 3 dòng log, 2 actor khác nhau
        List<AuditLog> logs = List.of(log(admin, "BAN_ACCOUNT", "t1"), log(mod, "UNBAN_ACCOUNT", "t2"),
                log(admin, "BAN_ACCOUNT", "t3"));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(logs));
        when(accountRepository.findAllById(any())).thenReturn(List.of(admin, mod));
        when(organizationProfileRepository.findAllById(any())).thenReturn(List.of());
        when(memberProfileRepository.findAllById(any())).thenReturn(List.of());
        when(moderatorProfileRepository.findAllById(any())).thenReturn(List.of(
                ModeratorProfile.builder().accountId(mod.getId()).fullName("Trần Mod").build()));

        PageResponse<AuditLogResponse> result = service.search(null, null, null, null, null, null, null, 0, 20);

        assertThat(result.getContent()).hasSize(3);
        AuditLogResponse first = result.getContent().get(0);
        assertThat(first.getId()).isEqualTo(logs.get(0).getId());
        assertThat(first.getActorEmail()).isEqualTo("admin@rehome.com");
        assertThat(first.getActorRole()).isEqualTo(Role.ADMIN);
        assertThat(first.getActorName()).isNull(); // Admin không có profile
        AuditLogResponse second = result.getContent().get(1);
        assertThat(second.getActorName()).isEqualTo("Trần Mod");
        assertThat(second.getActorRole()).isEqualTo(Role.MODERATOR);

        // mỗi repository chỉ bị gọi đúng 1 lần dù có 3 dòng log
        verify(accountRepository, times(1)).findAllById(any());
        verify(moderatorProfileRepository, times(1)).findAllById(any());
        verify(memberProfileRepository, times(1)).findAllById(any());
        verify(organizationProfileRepository, times(1)).findAllById(any());
    }

    @Test
    @DisplayName("search: log cũ không có detail trước/sau vẫn đọc được, detail trả nguyên (null)")
    @SuppressWarnings("unchecked")
    void search_legacyLogWithoutBeforeAfter() {
        Account admin = account(Role.ADMIN, "admin@rehome.com");
        AuditLog legacy = log(admin, "BAN_ACCOUNT", "t1");
        legacy.setDetail(mapper.createObjectNode().put("reason", "Spam"));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(legacy)));
        when(accountRepository.findAllById(any())).thenReturn(List.of(admin));
        when(organizationProfileRepository.findAllById(any())).thenReturn(List.of());
        when(moderatorProfileRepository.findAllById(any())).thenReturn(List.of());
        when(memberProfileRepository.findAllById(any())).thenReturn(List.of());

        AuditLogResponse row = service.search(null, null, null, null, null, null, null, 0, 20)
                .getContent().get(0);

        assertThat(row.getDetail().get("reason").asText()).isEqualTo("Spam");
        assertThat(row.getDetail().has("beforeStatus")).isFalse();
    }

    @Test
    @DisplayName("search: trang rỗng -> không truy vấn actor")
    @SuppressWarnings("unchecked")
    void search_emptyPage_noActorQueries() {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<AuditLogResponse> result = service.search(null, null, null, null, null, null, null, 0, 20);

        assertThat(result.getContent()).isEmpty();
        verifyNoInteractions(accountRepository, memberProfileRepository, moderatorProfileRepository,
                organizationProfileRepository);
    }

    @Test
    @DisplayName("search: from sau to -> INVALID_DATE_RANGE, không truy vấn")
    void search_invalidDateRange() {
        Instant from = Instant.parse("2026-11-05T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");

        assertThatThrownBy(() -> service.search(null, null, null, null, null, from, to, 0, 20))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DATE_RANGE);
        verify(auditLogRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("search: page/size sai -> INVALID_PAGINATION")
    void search_invalidPagination() {
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, null, -1, 20))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PAGINATION);
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, null, 0, 101))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PAGINATION);
    }

    @Test
    @DisplayName("filterOptions: trả actor và actionType thực tế, actor sắp theo tên (hoặc email nếu không có tên)")
    void filterOptions_returnsActorsAndActionTypes() {
        Account admin = account(Role.ADMIN, "zed@rehome.com");
        Account mod = account(Role.MODERATOR, "mod@rehome.com");
        when(auditLogRepository.findDistinctActionTypes()).thenReturn(List.of("BAN_ACCOUNT", "UNBAN_ACCOUNT"));
        when(auditLogRepository.findDistinctActorIds()).thenReturn(List.of(admin.getId(), mod.getId()));
        when(accountRepository.findAllById(any())).thenReturn(List.of(admin, mod));
        when(organizationProfileRepository.findAllById(any())).thenReturn(List.of());
        when(memberProfileRepository.findAllById(any())).thenReturn(List.of());
        when(moderatorProfileRepository.findAllById(any())).thenReturn(List.of(
                ModeratorProfile.builder().accountId(mod.getId()).fullName("An Mod").build()));

        AuditLogFilterOptionsResponse result = service.filterOptions();

        assertThat(result.getActionTypes()).containsExactly("BAN_ACCOUNT", "UNBAN_ACCOUNT");
        assertThat(result.getActors()).extracting(AuditLogFilterOptionsResponse.ActorOption::getEmail)
                .containsExactly("mod@rehome.com", "zed@rehome.com"); // "an mod" < "zed@rehome.com"
        assertThat(result.getActors().get(0).getName()).isEqualTo("An Mod");
        assertThat(result.getActors().get(1).getName()).isNull();
    }

    @Test
    @DisplayName("filterOptions: chưa có log nào -> danh sách rỗng, không truy vấn profile")
    void filterOptions_empty() {
        when(auditLogRepository.findDistinctActionTypes()).thenReturn(List.of());
        when(auditLogRepository.findDistinctActorIds()).thenReturn(List.of());

        AuditLogFilterOptionsResponse result = service.filterOptions();

        assertThat(result.getActors()).isEmpty();
        assertThat(result.getActionTypes()).isEmpty();
        verifyNoInteractions(accountRepository, memberProfileRepository, moderatorProfileRepository,
                organizationProfileRepository);
    }
}
