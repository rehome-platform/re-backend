package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.dto.response.AdminAccountResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.AuditLogRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.service.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** API 135 - tìm và xem tài khoản. Phần ban/unban nằm ở AccountAdminServiceImplTest. */
@ExtendWith(MockitoExtension.class)
class AccountAdminServiceImplSearchTest {

    @Mock AccountRepository accountRepository;
    @Mock RefreshTokenService refreshTokenService;
    @Mock AuditLogRepository auditLogRepository;
    @Mock MemberProfileRepository memberProfileRepository;
    @Mock ModeratorProfileRepository moderatorProfileRepository;
    @Mock OrganizationProfileRepository organizationProfileRepository;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks AccountAdminServiceImpl service;

    private Account account(Role role, String email) {
        return Account.builder().id(UUID.randomUUID()).email(email).role(role)
                .status(AccountStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("search: gộp họ tên/mã theo từng role (Member, Moderator, Tổ chức), Admin để null")
    void search_mapsNameAndCodePerRole() {
        Account member = account(Role.MEMBER, "member@rehome.com");
        Account moderator = account(Role.MODERATOR, "mod@rehome.com");
        Account org = account(Role.ORGANIZATION, "org@rehome.com");
        Account adminAcc = account(Role.ADMIN, "admin@rehome.com");

        when(accountRepository.searchForAdmin(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member, moderator, org, adminAcc)));
        when(memberProfileRepository.findAllById(any())).thenReturn(List.of(
                MemberProfile.builder().accountId(member.getId()).fullName("Nguyễn Văn An")
                        .memberCode("MB000001").build()));
        when(moderatorProfileRepository.findAllById(any())).thenReturn(List.of(
                ModeratorProfile.builder().accountId(moderator.getId()).fullName("Trần Mod")
                        .moderatorCode("MOD000001").build()));
        when(organizationProfileRepository.findAllById(any())).thenReturn(List.of(
                OrganizationProfile.builder().accountId(org.getId()).displayName("Quỹ Hy Vọng").build()));

        PageResponse<AdminAccountResponse> result = service.search(null, null, null, 0, 20);

        assertThat(result.getContent()).extracting(AdminAccountResponse::getFullName)
                .containsExactly("Nguyễn Văn An", "Trần Mod", "Quỹ Hy Vọng", null);
        assertThat(result.getContent()).extracting(AdminAccountResponse::getCode)
                .containsExactly("MB000001", "MOD000001", null, null);
    }

    @Test
    @DisplayName("search: từ khoá được trim, chuyển chữ thường và bọc % ; không có từ khoá thì dùng %%")
    void search_buildsPattern() {
        when(accountRepository.searchForAdmin(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(memberProfileRepository.findAllById(any())).thenReturn(List.of());
        when(moderatorProfileRepository.findAllById(any())).thenReturn(List.of());
        when(organizationProfileRepository.findAllById(any())).thenReturn(List.of());

        service.search("  NguYen@  ", Role.MEMBER, AccountStatus.BANNED, 0, 20);
        service.search(null, null, null, 0, 20);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(accountRepository).searchForAdmin(eq(Role.MEMBER), eq(AccountStatus.BANNED),
                pattern.capture(), any(Pageable.class));
        assertThat(pattern.getValue()).isEqualTo("%nguyen@%");
        verify(accountRepository).searchForAdmin(isNull(), isNull(), eq("%%"), any(Pageable.class));
    }

    @Test
    @DisplayName("search: size bị chặn tối đa 100, page âm đưa về 0")
    void search_clampsPaging() {
        when(accountRepository.searchForAdmin(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(memberProfileRepository.findAllById(any())).thenReturn(List.of());
        when(moderatorProfileRepository.findAllById(any())).thenReturn(List.of());
        when(organizationProfileRepository.findAllById(any())).thenReturn(List.of());

        service.search(null, null, null, -5, 9999);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(accountRepository).searchForAdmin(any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }
}
