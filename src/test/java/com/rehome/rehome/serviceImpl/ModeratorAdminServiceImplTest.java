package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.dto.request.ChangeModeratorWarehouseRequest;
import com.rehome.rehome.dto.request.CreateModeratorRequest;
import com.rehome.rehome.dto.response.ModeratorAdminResponse;
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
import com.rehome.rehome.service.OtpEmailService;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModeratorAdminServiceImplTest {

    @Mock AccountRepository accountRepository;
    @Mock ModeratorProfileRepository moderatorProfileRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock QueueItemRepository queueItemRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock OtpEmailService otpEmailService;
    @Mock AuditLogger auditLogger;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks ModeratorAdminServiceImpl service;

    Account admin;
    Warehouse activeWarehouse;
    Warehouse otherWarehouse;

    @BeforeEach
    void setup() {
        admin = Account.builder().id(UUID.randomUUID()).email("admin@rehome.com")
                .role(Role.ADMIN).status(AccountStatus.ACTIVE).build();
        activeWarehouse = Warehouse.builder().id(UUID.randomUUID()).name("Kho Quận 7")
                .status(CatalogStatus.ACTIVE).build();
        otherWarehouse = Warehouse.builder().id(UUID.randomUUID()).name("Kho Quận 1")
                .status(CatalogStatus.ACTIVE).build();
    }

    private void assertAppError(ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    private CreateModeratorRequest createRequest(String email, UUID warehouseId) {
        CreateModeratorRequest r = new CreateModeratorRequest();
        r.setEmail(email);
        r.setFullName("  Nguyễn Văn A  ");
        r.setWarehouseId(warehouseId);
        return r;
    }

    private Account moderatorIn(Warehouse warehouse) {
        return Account.builder().id(UUID.randomUUID()).email("mod@rehome.com")
                .role(Role.MODERATOR).status(AccountStatus.ACTIVE).warehouse(warehouse).build();
    }

    // ==================== create (API 138) ====================

    @Test
    @DisplayName("create: thành công -> tạo account MODERATOR + profile, gửi mail mật khẩu tạm, ghi audit")
    void create_success() {
        UUID whId = activeWarehouse.getId();
        when(accountRepository.existsByEmailIgnoreCase("new@rehome.com")).thenReturn(false);
        when(warehouseRepository.findById(whId)).thenReturn(Optional.of(activeWarehouse));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        when(moderatorProfileRepository.saveAndFlush(any(ModeratorProfile.class))).thenAnswer(inv -> {
            ModeratorProfile p = inv.getArgument(0);
            p.setModeratorCode("MOD000001");
            return p;
        });

        ModeratorAdminResponse response = service.create(admin, createRequest("  New@Rehome.com ", whId));

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account saved = accountCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("new@rehome.com");
        assertThat(saved.getRole()).isEqualTo(Role.MODERATOR);
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getWarehouse()).isSameAs(activeWarehouse);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(otpEmailService).sendModeratorAccount(eq("new@rehome.com"), eq("Nguyễn Văn A"),
                eq("MOD000001"), passwordCaptor.capture());
        assertThat(passwordCaptor.getValue()).hasSize(12);

        ArgumentCaptor<JsonNode> detail = ArgumentCaptor.forClass(JsonNode.class);
        verify(auditLogger).log(eq(admin), eq("CREATE_MODERATOR"), eq("ACCOUNT"),
                eq(saved.getId().toString()), detail.capture());
        assertThat(detail.getValue().get("moderatorCode").asText()).isEqualTo("MOD000001");
        // mật khẩu tạm không được lọt vào audit log
        assertThat(detail.getValue().toString()).doesNotContain(passwordCaptor.getValue());

        assertThat(response.getModeratorCode()).isEqualTo("MOD000001");
        assertThat(response.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.getWarehouseId()).isEqualTo(whId);
    }

    @Test
    @DisplayName("create: email đã tồn tại -> EMAIL_ALREADY_EXISTS, không gửi mail")
    void create_emailExists() {
        when(accountRepository.existsByEmailIgnoreCase("dup@rehome.com")).thenReturn(true);

        assertAppError(() -> service.create(admin, createRequest("dup@rehome.com", activeWarehouse.getId())),
                ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(otpEmailService, auditLogger);
    }

    @Test
    @DisplayName("create: kho không tồn tại -> WAREHOUSE_NOT_FOUND")
    void create_warehouseNotFound() {
        UUID missing = UUID.randomUUID();
        when(accountRepository.existsByEmailIgnoreCase("new@rehome.com")).thenReturn(false);
        when(warehouseRepository.findById(missing)).thenReturn(Optional.empty());

        assertAppError(() -> service.create(admin, createRequest("new@rehome.com", missing)),
                ErrorCode.WAREHOUSE_NOT_FOUND);
        verifyNoInteractions(otpEmailService);
    }

    @Test
    @DisplayName("create: kho đã ẩn -> WAREHOUSE_HIDDEN")
    void create_warehouseHidden() {
        activeWarehouse.setStatus(CatalogStatus.HIDDEN);
        when(accountRepository.existsByEmailIgnoreCase("new@rehome.com")).thenReturn(false);
        when(warehouseRepository.findById(activeWarehouse.getId())).thenReturn(Optional.of(activeWarehouse));

        assertAppError(() -> service.create(admin, createRequest("new@rehome.com", activeWarehouse.getId())),
                ErrorCode.WAREHOUSE_HIDDEN);
        verify(accountRepository, never()).save(any());
    }

    // ==================== changeWarehouse (API 140) ====================

    @Test
    @DisplayName("changeWarehouse: thành công -> đổi kho ở cả account lẫn profile, ghi audit")
    void changeWarehouse_success() {
        Account moderator = moderatorIn(activeWarehouse);
        ModeratorProfile profile = ModeratorProfile.builder().accountId(moderator.getId())
                .fullName("Mod A").moderatorCode("MOD000001").warehouse(activeWarehouse).build();
        when(accountRepository.findWithWarehouseById(moderator.getId())).thenReturn(Optional.of(moderator));
        when(warehouseRepository.findById(otherWarehouse.getId())).thenReturn(Optional.of(otherWarehouse));
        when(moderatorProfileRepository.findById(moderator.getId())).thenReturn(Optional.of(profile));
        when(queueItemRepository.existsByClaimedBy_Id(moderator.getId())).thenReturn(false);

        ChangeModeratorWarehouseRequest request = new ChangeModeratorWarehouseRequest();
        request.setWarehouseId(otherWarehouse.getId());
        ModeratorAdminResponse response = service.changeWarehouse(admin, moderator.getId(), request);

        assertThat(moderator.getWarehouse()).isSameAs(otherWarehouse);
        assertThat(profile.getWarehouse()).isSameAs(otherWarehouse);
        verify(accountRepository).save(moderator);
        verify(moderatorProfileRepository).save(profile);
        verify(auditLogger).log(eq(admin), eq("CHANGE_MODERATOR_WAREHOUSE"), eq("ACCOUNT"),
                eq(moderator.getId().toString()), any(JsonNode.class));
        assertThat(response.getWarehouseId()).isEqualTo(otherWarehouse.getId());
        assertThat(response.getModeratorCode()).isEqualTo("MOD000001");
    }

    @Test
    @DisplayName("changeWarehouse: Moderator đang giữ việc -> 409 MODERATOR_HAS_CLAIMED_TASK, không đổi gì")
    void changeWarehouse_hasClaimedTask() {
        Account moderator = moderatorIn(activeWarehouse);
        when(accountRepository.findWithWarehouseById(moderator.getId())).thenReturn(Optional.of(moderator));
        when(warehouseRepository.findById(otherWarehouse.getId())).thenReturn(Optional.of(otherWarehouse));
        when(moderatorProfileRepository.findById(moderator.getId())).thenReturn(Optional.empty());
        when(queueItemRepository.existsByClaimedBy_Id(moderator.getId())).thenReturn(true);

        ChangeModeratorWarehouseRequest request = new ChangeModeratorWarehouseRequest();
        request.setWarehouseId(otherWarehouse.getId());

        assertAppError(() -> service.changeWarehouse(admin, moderator.getId(), request),
                ErrorCode.MODERATOR_HAS_CLAIMED_TASK);
        assertThat(moderator.getWarehouse()).isSameAs(activeWarehouse);
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    @Test
    @DisplayName("changeWarehouse: lần đầu gán kho (chưa có kho cũ) -> không kiểm tra việc đang giữ")
    void changeWarehouse_firstAssignment_skipsQueueCheck() {
        Account moderator = moderatorIn(null);
        when(accountRepository.findWithWarehouseById(moderator.getId())).thenReturn(Optional.of(moderator));
        when(warehouseRepository.findById(otherWarehouse.getId())).thenReturn(Optional.of(otherWarehouse));
        when(moderatorProfileRepository.findById(moderator.getId())).thenReturn(Optional.empty());

        ChangeModeratorWarehouseRequest request = new ChangeModeratorWarehouseRequest();
        request.setWarehouseId(otherWarehouse.getId());
        service.changeWarehouse(admin, moderator.getId(), request);

        assertThat(moderator.getWarehouse()).isSameAs(otherWarehouse);
        verifyNoInteractions(queueItemRepository);
    }

    @Test
    @DisplayName("changeWarehouse: kho đích đang HIDDEN -> 409 WAREHOUSE_HIDDEN")
    void changeWarehouse_targetHidden() {
        Account moderator = moderatorIn(activeWarehouse);
        otherWarehouse.setStatus(CatalogStatus.HIDDEN);
        when(accountRepository.findWithWarehouseById(moderator.getId())).thenReturn(Optional.of(moderator));
        when(warehouseRepository.findById(otherWarehouse.getId())).thenReturn(Optional.of(otherWarehouse));

        ChangeModeratorWarehouseRequest request = new ChangeModeratorWarehouseRequest();
        request.setWarehouseId(otherWarehouse.getId());

        assertAppError(() -> service.changeWarehouse(admin, moderator.getId(), request),
                ErrorCode.WAREHOUSE_HIDDEN);
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeWarehouse: tài khoản không phải Moderator -> NOT_MODERATOR_ACCOUNT")
    void changeWarehouse_notModerator() {
        Account member = Account.builder().id(UUID.randomUUID()).email("m@rehome.com")
                .role(Role.MEMBER).status(AccountStatus.ACTIVE).build();
        when(accountRepository.findWithWarehouseById(member.getId())).thenReturn(Optional.of(member));

        ChangeModeratorWarehouseRequest request = new ChangeModeratorWarehouseRequest();
        request.setWarehouseId(otherWarehouse.getId());

        assertAppError(() -> service.changeWarehouse(admin, member.getId(), request),
                ErrorCode.NOT_MODERATOR_ACCOUNT);
    }

    @Test
    @DisplayName("changeWarehouse: không tìm thấy tài khoản -> ACCOUNT_NOT_FOUND")
    void changeWarehouse_accountNotFound() {
        UUID missing = UUID.randomUUID();
        when(accountRepository.findWithWarehouseById(missing)).thenReturn(Optional.empty());

        ChangeModeratorWarehouseRequest request = new ChangeModeratorWarehouseRequest();
        request.setWarehouseId(otherWarehouse.getId());

        assertAppError(() -> service.changeWarehouse(admin, missing, request), ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("changeWarehouse: đã ở đúng kho đó -> MODERATOR_SAME_WAREHOUSE")
    void changeWarehouse_sameWarehouse() {
        Account moderator = moderatorIn(activeWarehouse);
        when(accountRepository.findWithWarehouseById(moderator.getId())).thenReturn(Optional.of(moderator));
        when(warehouseRepository.findById(activeWarehouse.getId())).thenReturn(Optional.of(activeWarehouse));
        when(moderatorProfileRepository.findById(moderator.getId())).thenReturn(Optional.empty());

        ChangeModeratorWarehouseRequest request = new ChangeModeratorWarehouseRequest();
        request.setWarehouseId(activeWarehouse.getId());

        assertAppError(() -> service.changeWarehouse(admin, moderator.getId(), request),
                ErrorCode.MODERATOR_SAME_WAREHOUSE);
        verify(accountRepository, never()).save(any());
    }
}
