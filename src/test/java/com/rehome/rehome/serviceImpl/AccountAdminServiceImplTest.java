package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.dto.response.AccountStatusResponse;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.AuditLogRepository;
import com.rehome.rehome.service.RefreshTokenService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAdminServiceImplTest {

    @Mock
    AccountRepository accountRepository;

    @Mock
    RefreshTokenService refreshTokenService;

    @Mock
    AuditLogRepository auditLogRepository;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    AccountAdminServiceImpl service;

    Account admin;
    Account target;
    UUID targetId;

    @BeforeEach
    void setup() {
        admin = Account.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .role(Role.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build();

        targetId = UUID.randomUUID();
        target = Account.builder()
                .id(targetId)
                .email("member@example.com")
                .role(Role.MEMBER)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    private void assertAppError(ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    private void verifyNothingWritten() {
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService);
        verifyNoInteractions(auditLogRepository);
    }

    // ==================== ban ====================

    @Test
    @DisplayName("ban: thành công -> BANNED, lưu lý do, thu hồi refresh token, ghi audit, trả response")
    void ban_success() {
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(target));

        AccountStatusResponse response = service.ban(admin, targetId, "Spam nhiều lần");

        assertThat(target.getStatus()).isEqualTo(AccountStatus.BANNED);
        assertThat(target.getBanReason()).isEqualTo("Spam nhiều lần");
        verify(accountRepository).save(target);
        verify(refreshTokenService).revokeAllAccountTokens(target);

        assertThat(response.getAccountId()).isEqualTo(targetId.toString());
        assertThat(response.getStatus()).isEqualTo(AccountStatus.BANNED);
        assertThat(response.getBanReason()).isEqualTo("Spam nhiều lần");
    }

    @Test
    @DisplayName("ban: lý do được trim khoảng trắng hai đầu")
    void ban_reasonIsTrimmed() {
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(target));

        AccountStatusResponse response = service.ban(admin, targetId, "   Lừa đảo   ");

        assertThat(target.getBanReason()).isEqualTo("Lừa đảo");
        assertThat(response.getBanReason()).isEqualTo("Lừa đảo");
    }

    @Test
    @DisplayName("ban: ghi audit log BAN_ACCOUNT đúng actor, target và lý do")
    void ban_writesAuditLog() {
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(target));

        service.ban(admin, targetId, "Spam");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertThat(log.getActorAccount()).isSameAs(admin);
        assertThat(log.getActionType()).isEqualTo("BAN_ACCOUNT");
        assertThat(log.getTargetType()).isEqualTo("ACCOUNT");
        assertThat(log.getTargetId()).isEqualTo(targetId.toString());
        assertThat(log.getDetail().get("reason").asText()).isEqualTo("Spam");
    }

    @Test
    @DisplayName("ban: tài khoản không tồn tại -> ACCOUNT_NOT_FOUND, không ghi gì")
    void ban_targetNotFound_throws() {
        when(accountRepository.findById(targetId)).thenReturn(Optional.empty());

        assertAppError(() -> service.ban(admin, targetId, "Spam"), ErrorCode.ACCOUNT_NOT_FOUND);

        verifyNothingWritten();
    }

    @Test
    @DisplayName("ban: không được khoá tài khoản ADMIN khác")
    void ban_targetIsAdmin_throws() {
        target.setRole(Role.ADMIN);
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertAppError(() -> service.ban(admin, targetId, "Spam"), ErrorCode.CANNOT_BAN_ADMIN_OR_SELF);

        assertThat(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verifyNothingWritten();
    }

    @Test
    @DisplayName("ban: không được tự khoá chính mình")
    void ban_targetIsSelf_throws() {
        when(accountRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertAppError(() -> service.ban(admin, admin.getId(), "Spam"), ErrorCode.CANNOT_BAN_ADMIN_OR_SELF);

        assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verifyNothingWritten();
    }

    @Test
    @DisplayName("ban: tự khoá mình bị chặn kể cả khi tài khoản không phải ADMIN (so theo id)")
    void ban_selfCheckByIdEvenWhenNotAdminRole() {
        Account moderator = Account.builder()
                .id(UUID.randomUUID())
                .email("mod@example.com")
                .role(Role.MODERATOR)
                .status(AccountStatus.ACTIVE)
                .build();
        when(accountRepository.findById(moderator.getId())).thenReturn(Optional.of(moderator));

        assertAppError(() -> service.ban(moderator, moderator.getId(), "Spam"),
                ErrorCode.CANNOT_BAN_ADMIN_OR_SELF);

        verifyNothingWritten();
    }

    @Test
    @DisplayName("ban: tài khoản đã bị khoá -> ACCOUNT_ALREADY_BANNED, không ghi đè lý do cũ")
    void ban_alreadyBanned_throws() {
        target.setStatus(AccountStatus.BANNED);
        target.setBanReason("Lý do cũ");
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertAppError(() -> service.ban(admin, targetId, "Lý do mới"), ErrorCode.ACCOUNT_ALREADY_BANNED);

        assertThat(target.getBanReason()).isEqualTo("Lý do cũ");
        verifyNothingWritten();
    }

    // ==================== unban ====================

    @Test
    @DisplayName("unban: thành công -> ACTIVE, xoá lý do, ghi audit, trả response")
    void unban_success() {
        target.setStatus(AccountStatus.BANNED);
        target.setBanReason("Spam");
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(target));

        AccountStatusResponse response = service.unban(admin, targetId);

        assertThat(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(target.getBanReason()).isNull();
        verify(accountRepository).save(target);

        assertThat(response.getAccountId()).isEqualTo(targetId.toString());
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.getBanReason()).isNull();
    }

    @Test
    @DisplayName("unban: audit UNBAN_ACCOUNT lưu lại lý do khoá trước đó")
    void unban_writesAuditLogWithPreviousReason() {
        target.setStatus(AccountStatus.BANNED);
        target.setBanReason("Spam");
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(target));

        service.unban(admin, targetId);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertThat(log.getActorAccount()).isSameAs(admin);
        assertThat(log.getActionType()).isEqualTo("UNBAN_ACCOUNT");
        assertThat(log.getTargetType()).isEqualTo("ACCOUNT");
        assertThat(log.getTargetId()).isEqualTo(targetId.toString());
        assertThat(log.getDetail().get("previousReason").asText()).isEqualTo("Spam");
    }

    @Test
    @DisplayName("unban: không thu hồi refresh token (chỉ ban mới thu hồi)")
    void unban_doesNotRevokeTokens() {
        target.setStatus(AccountStatus.BANNED);
        target.setBanReason("Spam");
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(target));

        service.unban(admin, targetId);

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    @DisplayName("unban: tài khoản không tồn tại -> ACCOUNT_NOT_FOUND")
    void unban_targetNotFound_throws() {
        when(accountRepository.findById(targetId)).thenReturn(Optional.empty());

        assertAppError(() -> service.unban(admin, targetId), ErrorCode.ACCOUNT_NOT_FOUND);

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("unban: tài khoản đang ACTIVE (không bị khoá) -> ACCOUNT_NOT_BANNED")
    void unban_notBanned_throws() {
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertAppError(() -> service.unban(admin, targetId), ErrorCode.ACCOUNT_NOT_BANNED);

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(auditLogRepository);
    }
}
